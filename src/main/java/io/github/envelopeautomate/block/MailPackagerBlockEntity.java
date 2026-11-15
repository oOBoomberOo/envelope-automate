package io.github.envelopeautomate.block;

import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.mail.MailboxLink;
import io.github.envelopeautomate.menu.MailPackagerMenu;
import io.github.envelopeautomate.stamp.StampSeals;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.network.packet.clientbound.ClientboundMailboxMenuSetMailPacket;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.inventory.MailboxMenu;
import io.github.mortuusars.envelope.world.item.AddressTagItem;
import io.github.mortuusars.envelope.world.item.PackageItem;
import io.github.mortuusars.envelope.world.item.SealStampItem;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import io.github.mortuusars.envelope.world.mail.address.Address;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailPackagerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BUFFER_SLOTS = 18;
    public static final int CONFIG_ADDRESS_TAG = 0;
    public static final int CONFIG_SEAL_STAMP = 1;

    public static final int DATA_MAILBOX_ATTACHED = 0;
    public static final int DATA_DIRECT = 1;
    public static final int DATA_COUNT = 2;

    private static final int TICK_INTERVAL = 4;

    /**
     * While powered, the buffer is packed automatically once nothing has been inserted or removed for this long.
     */
    public static final int SETTLE_TICKS = 30;

    private final ItemStackHandler input = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return PackageContents.canHold(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (!packing && level != null) {
                lastInputChangeTime = level.getGameTime();
            }
            setChanged();
        }
    };

    private final ItemStackHandler output = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler config = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isValidConfigItem(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler outgoing = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final IItemHandler automationHandler = new AutomationHandler();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_MAILBOX_ATTACHED -> mailboxAttached ? 1 : 0;
                case DATA_DIRECT -> directMode ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_DIRECT) {
                directMode = value != 0;
                setChanged();
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private @Nullable UUID ownerId;
    private String ownerName = "";
    private boolean mailboxAttached;
    private boolean directMode;
    private long lastInputChangeTime;
    private boolean packing;

    public MailPackagerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MAIL_PACKAGER_BE.get(), pos, state);
    }

    public static boolean isValidConfigItem(int slot, ItemStack stack) {
        return switch (slot) {
            case CONFIG_ADDRESS_TAG -> stack.getItem() instanceof AddressTagItem;
            case CONFIG_SEAL_STAMP -> stack.getItem() instanceof SealStampItem;
            default -> false;
        };
    }

    // -- Owner (the seal signature is made on behalf of the owner)

    public void setOwner(Player player) {
        ownerId = player.getUUID();
        ownerName = player.getScoreboardName();
        setChanged();
    }

    public IItemHandler getAutomationHandler() {
        return automationHandler;
    }

    // -- Menu

    public void openMenu(ServerPlayer player) {
        if (ownerId == null) {
            setOwner(player);
        }
        player.openMenu(this, buffer -> buffer.writeBlockPos(getBlockPos()));
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.envelope_automate.mail_packager");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MailPackagerMenu(containerId, inventory, getBlockPos(), input, output, config, outgoing, dataAccess);
    }

    // -- Ticking

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % TICK_INTERVAL != 0) {
            return;
        }

        @Nullable MailboxBlockEntity mailbox = MailboxLink.findAttached(serverLevel, getBlockPos());
        mailboxAttached = mailbox != null;

        if (mailbox != null) {
            postOutgoing(mailbox);
            unpackIncoming(serverLevel, mailbox);
        }

        if (shouldAutoPack(serverLevel)) {
            pack();
        }
    }

    /**
     * While powered, pack automatically as soon as either:
     * <ul>
     *     <li>the buffer holds a full box — all {@link PackageContents#SLOTS} slots completely filled, so a steady
     *     item stream is sent as full packages instead of waiting for it to stop; or</li>
     *     <li>the buffer has settled (no inserts/removals for {@link #SETTLE_TICKS}).</li>
     * </ul>
     * Changes made by packing itself don't count, so a buffer holding more than one package keeps packing
     * as soon as the previous package has been posted.
     */
    private boolean shouldAutoPack(ServerLevel level) {
        if (!getBlockState().getValue(MailPackagerBlock.POWERED)
              || !outgoing.getStackInSlot(0).isEmpty()
              || isInputEmpty()) {
            return false;
        }

        return isFullPackage() || level.getGameTime() - lastInputChangeTime >= SETTLE_TICKS;
    }

    private boolean isInputEmpty() {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (!input.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the buffer would fill a package completely: every one of its {@link PackageContents#SLOTS}
     * slots taken up by a full stack (a single item, for stacks of one).
     */
    private boolean isFullPackage() {
        List<ItemStack> contents = collectContents();
        if (contents.size() < PackageContents.SLOTS) {
            return false;
        }

        for (ItemStack stack : contents) {
            if (stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    // -- Packing

    /**
     * Called on a rising redstone edge, and automatically (see {@link #shouldAutoPack}). Packs up to one package
     * worth of buffered items, addresses it with the address tag's address, seals it with the seal stamp (if
     * present) and posts it to the attached mailbox. Neither the address tag nor the seal stamp is consumed.
     */
    public void pack() {
        if (!(level instanceof ServerLevel serverLevel) || !outgoing.getStackInSlot(0).isEmpty()) {
            return;
        }

        @Nullable Address address = config.getStackInSlot(CONFIG_ADDRESS_TAG).get(Envelope.DataComponents.ADDRESS_TAG_ADDRESS);
        if (address == null) {
            return;
        }

        List<ItemStack> contents = collectContents();
        if (contents.isEmpty()) {
            return;
        }

        packing = true;
        try {
            consume(contents);
        } finally {
            packing = false;
        }

        // Direct packages are picked up from the mailbox by DirectMailDispatcher instead of a courier.
        PackageItem packageItem = directMode ? ModRegistry.DIRECT_PACKAGE.get() : Envelope.Items.PACKAGE.get();
        ItemStack pkg = new ItemStack(packageItem);
        pkg.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(contents));
        pkg.set(Envelope.DataComponents.MAIL_ADDRESS_TAG, address);

        ItemStack stamp = config.getStackInSlot(CONFIG_SEAL_STAMP);
        if (stamp.getItem() instanceof SealStampItem) {
            StampSeals.Signer signer = StampSeals.signerOf(stamp, serverLevel.getServer(), new StampSeals.Signer(ownerName, ownerId));
            pkg = packageItem.seal(serverLevel, pkg, StampSeals.create(stamp, signer, serverLevel.registryAccess()));
        }

        outgoing.setStackInSlot(0, pkg);

        serverLevel.playSound(null, getBlockPos(), Envelope.SoundEvents.PACKAGE_PACK.get(), SoundSource.BLOCKS,
              0.8f, serverLevel.getRandom().nextFloat() * 0.3f + 0.85f);

        @Nullable MailboxBlockEntity mailbox = MailboxLink.findAttached(serverLevel, getBlockPos());
        if (mailbox != null) {
            postOutgoing(mailbox);
        }
    }

    /**
     * What one package would hold right now: up to {@link PackageContents#SLOTS} stacks worth of buffered items,
     * merging partial stacks of the same item together. Does not modify the buffer.
     */
    private List<ItemStack> collectContents() {
        List<ItemStack> contents = new ArrayList<>();

        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            int remaining = stack.getCount();

            for (ItemStack content : contents) {
                if (remaining <= 0) break;
                if (ItemStack.isSameItemSameComponents(content, stack)) {
                    int moved = Math.min(remaining, content.getMaxStackSize() - content.getCount());
                    if (moved > 0) {
                        content.grow(moved);
                        remaining -= moved;
                    }
                }
            }

            while (remaining > 0 && contents.size() < PackageContents.SLOTS) {
                int moved = Math.min(remaining, stack.getMaxStackSize());
                contents.add(stack.copyWithCount(moved));
                remaining -= moved;
            }
        }

        return contents;
    }

    /**
     * Removes the given amounts from the input buffer (as chosen by {@link #collectContents}).
     */
    private void consume(List<ItemStack> contents) {
        for (ItemStack content : contents) {
            int remaining = content.getCount();
            for (int slot = 0; slot < input.getSlots() && remaining > 0; slot++) {
                ItemStack stack = input.getStackInSlot(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, content)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                input.extractItem(slot, taken, false);
                remaining -= taken;
            }
        }
    }

    private void postOutgoing(MailboxBlockEntity mailbox) {
        ItemStack pending = outgoing.getStackInSlot(0);
        if (!pending.isEmpty() && MailboxLink.post(mailbox, pending)) {
            outgoing.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    // -- Unpacking

    private void unpackIncoming(ServerLevel level, MailboxBlockEntity mailbox) {
        List<ItemStack> inbox = mailbox.getAllMail();

        for (int index = 0; index < inbox.size(); index++) {
            ItemStack mail = inbox.get(index);
            if (!isUnpackable(level, mail)) {
                continue;
            }

            ItemStack pkg = mail.copy();
            PackageItem packageItem = (PackageItem) pkg.getItem();
            Vec3 center = Vec3.atCenterOf(getBlockPos());

            List<ItemStack> contents = packageItem.unpack(pkg, level, center, null).stream()
                  .filter(stack -> !stack.isEmpty())
                  .toList();

            if (!fitsIntoOutput(contents)) {
                continue;
            }

            syncOpenMenus(level, mailbox);
            mailbox.removeMail(index);

            for (ItemStack stack : contents) {
                ItemHandlerHelper.insertItemStacked(output, stack.copy(), false);
            }

            packageItem.dropExperience(pkg, level, center);
            level.playSound(null, getBlockPos(), Envelope.SoundEvents.PAPER_TEAR.get(), SoundSource.BLOCKS,
                  0.6f, level.getRandom().nextFloat() * 0.1f + 0.9f);
            return; // One package per cycle
        }
    }

    /**
     * Envelope's mailbox menu keeps its own copy of the inbox and is only told about removals (by index), not
     * about mail that arrived while it was open. Removing such mail would make the menu remove an index its
     * copy doesn't have and crash the server, so bring every open menu up to date first.
     */
    private static void syncOpenMenus(ServerLevel level, MailboxBlockEntity mailbox) {
        MailboxMenu.executeForPlayersWithMenu(level, mailbox.getAddress(), (player, menu) -> {
            menu.setMail(mailbox.getAllMail());
            new ClientboundMailboxMenuSetMailPacket(mailbox.getAllMail()).sendToClient(player);
        });
    }

    private static boolean isUnpackable(ServerLevel level, ItemStack mail) {
        if (!mail.is(Envelope.Items.PACKAGE.get()) && !mail.is(Envelope.Items.SEALED_PACKAGE.get())
              && !mail.is(ModRegistry.DIRECT_PACKAGE.get()) && !mail.is(ModRegistry.SEALED_DIRECT_PACKAGE.get())) {
            return false;
        }

        @Nullable Seal seal = mail.get(Envelope.DataComponents.SEAL);
        return seal == null || seal.lock().map(lock -> !lock.isLocked(level)).orElse(true);
    }

    private boolean fitsIntoOutput(List<ItemStack> stacks) {
        ItemStackHandler simulation = new ItemStackHandler(output.getSlots());
        for (int slot = 0; slot < output.getSlots(); slot++) {
            simulation.setStackInSlot(slot, output.getStackInSlot(slot).copy());
        }

        for (ItemStack stack : stacks) {
            if (!ItemHandlerHelper.insertItemStacked(simulation, stack.copy(), false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // -- Drops

    public void dropContents() {
        if (level == null) {
            return;
        }

        for (ItemStackHandler handler : List.of(input, output, config, outgoing)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), stack.copy());
                    handler.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    // -- Saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("input", input.serializeNBT(registries));
        tag.put("output", output.serializeNBT(registries));
        tag.put("config", config.serializeNBT(registries));
        tag.put("outgoing", outgoing.serializeNBT(registries));
        if (ownerId != null) {
            tag.putUUID("owner", ownerId);
        }
        if (!ownerName.isEmpty()) {
            tag.putString("owner_name", ownerName);
        }
        tag.putBoolean("direct_mode", directMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input.deserializeNBT(registries, tag.getCompound("input"));
        output.deserializeNBT(registries, tag.getCompound("output"));
        config.deserializeNBT(registries, tag.getCompound("config"));
        outgoing.deserializeNBT(registries, tag.getCompound("outgoing"));
        ownerId = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        directMode = tag.getBoolean("direct_mode");
    }

    /**
     * Pipes insert into the input buffer and extract from the output buffer.
     * Slots 0-17 are input (insert only), slots 18-35 are output (extract only).
     */
    private class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return BUFFER_SLOTS * 2;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot < BUFFER_SLOTS ? input.getStackInSlot(slot) : output.getStackInSlot(slot - BUFFER_SLOTS);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot < BUFFER_SLOTS ? input.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < BUFFER_SLOTS ? ItemStack.EMPTY : output.extractItem(slot - BUFFER_SLOTS, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < BUFFER_SLOTS ? input.getSlotLimit(slot) : output.getSlotLimit(slot - BUFFER_SLOTS);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot < BUFFER_SLOTS && input.isItemValid(slot, stack);
        }
    }
}

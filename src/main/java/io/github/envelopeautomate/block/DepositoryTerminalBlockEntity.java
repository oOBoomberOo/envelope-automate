package io.github.envelopeautomate.block;

import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.mail.MailboxLink;
import io.github.envelopeautomate.menu.DepositoryTerminalMenu;
import io.github.envelopeautomate.stamp.StampSeals;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DepositoryTerminalBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 10;

    /**
     * Orders waiting for the attached mailbox's outgoing slot to become free.
     */
    private final List<ItemStack> outbox = new ArrayList<>();

    /**
     * Inserted seal stamp. With a fingerprint, the terminal works on the fingerprinted player's account.
     * Its material/die are also used for order seals.
     */
    private ItemStack stamp = ItemStack.EMPTY;

    public DepositoryTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DEPOSITORY_TERMINAL_BE.get(), pos, state);
    }

    // -- Stamp

    public ItemStack getStamp() {
        return stamp;
    }

    /**
     * Puts one stamp from the stack into the terminal and returns the previously inserted stamp (or empty).
     */
    public ItemStack swapStamp(ItemStack newStamp) {
        ItemStack previous = stamp;
        stamp = newStamp.copyWithCount(1);
        setChanged();
        return previous;
    }

    public ItemStack removeStamp() {
        ItemStack previous = stamp;
        stamp = ItemStack.EMPTY;
        setChanged();
        return previous;
    }

    /**
     * The account the terminal shows and withdraws from for this viewer.
     */
    public StampSeals.Signer getSignerFor(ServerPlayer viewer) {
        return StampSeals.signerOf(stamp, viewer.server, new StampSeals.Signer(viewer.getScoreboardName(), viewer.getUUID()));
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new DepositoryTerminalMenu(containerId, inventory, getBlockPos()),
                    Component.translatable("block.envelope_automate.depository_terminal")),
              buffer -> buffer.writeBlockPos(getBlockPos()));
        // The menu sends the stock snapshot itself and keeps it up to date while open
    }

    public @Nullable MailboxBlockEntity getAttachedMailbox() {
        return level != null ? MailboxLink.findAttached(level, getBlockPos()) : null;
    }

    public Optional<String> getAttachedMailboxAddress() {
        @Nullable MailboxBlockEntity mailbox = getAttachedMailbox();
        if (mailbox == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mailbox.getAddress().getString());
        } catch (RuntimeException e) {
            return Optional.empty(); // Mailbox address is not assigned yet
        }
    }

    /**
     * Queues an order letter to be posted at the attached mailbox.
     */
    public void post(ItemStack letter) {
        outbox.add(letter.copy());
        setChanged();
        flushOutbox();
    }

    public void serverTick() {
        if (level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % TICK_INTERVAL == 0 && !outbox.isEmpty()) {
            flushOutbox();
        }
    }

    private void flushOutbox() {
        @Nullable MailboxBlockEntity mailbox = getAttachedMailbox();
        if (mailbox != null && !outbox.isEmpty() && MailboxLink.post(mailbox, outbox.getFirst())) {
            outbox.removeFirst();
            setChanged();
        }
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (ItemStack stack : outbox) {
            Containers.dropItemStack(level, getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), stack);
        }
        outbox.clear();
        if (!stamp.isEmpty()) {
            Containers.dropItemStack(level, getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), stamp);
            stamp = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!outbox.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack stack : outbox) {
                list.add(stack.save(registries));
            }
            tag.put("outbox", list);
        }
        if (!stamp.isEmpty()) {
            tag.put("stamp", stamp.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outbox.clear();
        ListTag list = tag.getList("outbox", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(outbox::add);
        }
        stamp = ItemStack.parseOptional(registries, tag.getCompound("stamp"));
    }
}

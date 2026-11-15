package io.github.envelopeautomate.menu;

import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.block.MailPackagerBlockEntity;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class MailPackagerMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 222;

    public static final int INPUT_Y = 18;
    public static final int CONFIG_Y = 59;
    public static final int OUTPUT_Y = 91;
    public static final int INVENTORY_Y = 140;
    public static final int HOTBAR_Y = 198;

    public static final int ADDRESS_SLOT_X = 8;
    public static final int SEAL_SLOT_X = 26;
    public static final int OUTGOING_SLOT_X = 152;

    public static final int INPUT_START = 0;
    public static final int OUTPUT_START = INPUT_START + MailPackagerBlockEntity.BUFFER_SLOTS;
    public static final int ADDRESS_SLOT = OUTPUT_START + MailPackagerBlockEntity.BUFFER_SLOTS;
    public static final int SEAL_SLOT = ADDRESS_SLOT + 1;
    public static final int OUTGOING_SLOT = SEAL_SLOT + 1;
    public static final int PLAYER_START = OUTGOING_SLOT + 1;
    public static final int PLAYER_END = PLAYER_START + 36;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public MailPackagerMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler input, IItemHandler output,
                            IItemHandler config, IItemHandler outgoing, ContainerData data) {
        super(ModRegistry.MAIL_PACKAGER_MENU.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new InputSlot(input, column + row * 9, 8 + column * 18, INPUT_Y + row * 18));
            }
        }

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new TakeOnlySlot(output, column + row * 9, 8 + column * 18, OUTPUT_Y + row * 18));
            }
        }

        addSlot(new ConfigSlot(config, MailPackagerBlockEntity.CONFIG_ADDRESS_TAG, ADDRESS_SLOT_X, CONFIG_Y));
        addSlot(new ConfigSlot(config, MailPackagerBlockEntity.CONFIG_SEAL_STAMP, SEAL_SLOT_X, CONFIG_Y));
        addSlot(new TakeOnlySlot(outgoing, 0, OUTGOING_SLOT_X, CONFIG_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
        }

        addDataSlots(data);
    }

    public static MailPackagerMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return new MailPackagerMenu(containerId, inventory, buffer.readBlockPos(),
              new ItemStackHandler(MailPackagerBlockEntity.BUFFER_SLOTS),
              new ItemStackHandler(MailPackagerBlockEntity.BUFFER_SLOTS),
              new ItemStackHandler(2),
              new ItemStackHandler(1),
              new SimpleContainerData(MailPackagerBlockEntity.DATA_COUNT));
    }

    public boolean isMailboxAttached() {
        return data.get(MailPackagerBlockEntity.DATA_MAILBOX_ATTACHED) == 1;
    }

    public ItemStack getAddressTag() {
        return slots.get(ADDRESS_SLOT).getItem();
    }

    public ItemStack getSealStamp() {
        return slots.get(SEAL_SLOT).getItem();
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (MailPackagerBlockEntity.isValidConfigItem(MailPackagerBlockEntity.CONFIG_ADDRESS_TAG, stack)) {
                moved = moveItemStackTo(stack, ADDRESS_SLOT, ADDRESS_SLOT + 1, false);
            } else if (MailPackagerBlockEntity.isValidConfigItem(MailPackagerBlockEntity.CONFIG_SEAL_STAMP, stack)) {
                moved = moveItemStackTo(stack, SEAL_SLOT, SEAL_SLOT + 1, false);
            }
            // Config items only go into the packing buffer when their config slot is already occupied
            if (!moved && PackageContents.canHold(stack)) {
                moved = moveItemStackTo(stack, INPUT_START, OUTPUT_START, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModRegistry.MAIL_PACKAGER.get());
    }

    // -- Slots

    private static class InputSlot extends SlotItemHandler {
        public InputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return !stack.isEmpty() && PackageContents.canHold(stack);
        }
    }

    private static class TakeOnlySlot extends SlotItemHandler {
        public TakeOnlySlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }
    }

    public static class ConfigSlot extends SlotItemHandler {
        private final int configIndex;

        public ConfigSlot(IItemHandler handler, int configIndex, int x, int y) {
            super(handler, configIndex, x, y);
            this.configIndex = configIndex;
        }

        public int getConfigIndex() {
            return configIndex;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return MailPackagerBlockEntity.isValidConfigItem(configIndex, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(@NotNull ItemStack stack) {
            return 1;
        }
    }
}

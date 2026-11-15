package io.github.envelopeautomate.client;

import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.menu.MailPackagerMenu;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.mail.address.Address;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MailPackagerScreen extends AbstractContainerScreen<MailPackagerMenu> {
    private static final int TEXT_COLOR = 0x404040;
    private static final int OK_COLOR = 0x2E7D32;
    private static final int WARN_COLOR = 0xB71C1C;
    private static final int DIRECT_BUTTON_COLOR = 0xFFA9CBE8;

    private final ItemStack addressTagIcon = new ItemStack(Envelope.Items.ADDRESS_TAG.get());
    private final ItemStack sealStampIcon = new ItemStack(Envelope.Items.SEAL_STAMP.get());
    private final ItemStack packageIcon = new ItemStack(Envelope.Items.PACKAGE.get());
    private final ItemStack directPackageIcon = new ItemStack(ModRegistry.DIRECT_PACKAGE.get());

    public MailPackagerScreen(MailPackagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MailPackagerMenu.IMAGE_WIDTH;
        imageHeight = MailPackagerMenu.IMAGE_HEIGHT;
        inventoryLabelY = MailPackagerMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiDraw.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            GuiDraw.slot(graphics, leftPos + slot.x, topPos + slot.y);
        }

        renderGhost(graphics, MailPackagerMenu.ADDRESS_SLOT, addressTagIcon);
        renderGhost(graphics, MailPackagerMenu.SEAL_SLOT, sealStampIcon);
        renderGhost(graphics, MailPackagerMenu.OUTGOING_SLOT, currentPackageIcon());

        // Regular / Direct Mail toggle
        int buttonX = leftPos + MailPackagerMenu.DIRECT_BUTTON_X;
        int buttonY = topPos + MailPackagerMenu.CONFIG_Y;
        boolean hovered = isOverDirectButton(mouseX, mouseY);
        graphics.fill(buttonX - 1, buttonY - 1, buttonX + 17, buttonY + 17, hovered ? GuiDraw.HIGHLIGHT : GuiDraw.BLACK);
        graphics.fill(buttonX, buttonY, buttonX + 16, buttonY + 16, menu.isDirectMode() ? DIRECT_BUTTON_COLOR : GuiDraw.BODY);
        graphics.renderFakeItem(currentPackageIcon(), buttonX, buttonY);
    }

    private ItemStack currentPackageIcon() {
        return menu.isDirectMode() ? directPackageIcon : packageIcon;
    }

    private boolean isOverDirectButton(double mouseX, double mouseY) {
        int x = leftPos + MailPackagerMenu.DIRECT_BUTTON_X;
        int y = topPos + MailPackagerMenu.CONFIG_Y;
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverDirectButton(mouseX, mouseY) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MailPackagerMenu.BUTTON_ID_DIRECT);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderGhost(GuiGraphics graphics, int slotIndex, ItemStack icon) {
        Slot slot = menu.slots.get(slotIndex);
        if (slot.hasItem()) {
            return;
        }
        int x = leftPos + slot.x;
        int y = topPos + slot.y;
        graphics.renderFakeItem(icon, x, y);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        graphics.fill(x, y, x + 16, y + 16, 0xA88B8B8B);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        graphics.drawString(font, Component.translatable("gui.envelope_automate.mail_packager.unpacked"),
              8, MailPackagerMenu.OUTPUT_Y - 11, TEXT_COLOR, false);

        int textX = MailPackagerMenu.SEAL_SLOT_X + 22;
        int maxWidth = MailPackagerMenu.DIRECT_BUTTON_X - 4 - textX;

        @Nullable Address address = menu.getAddressTag().get(Envelope.DataComponents.ADDRESS_TAG_ADDRESS);
        Component addressLine = address != null
              ? Component.translatable("gui.envelope_automate.mail_packager.to", address.getString())
              : Component.translatable("gui.envelope_automate.mail_packager.no_address");
        drawClipped(graphics, addressLine, textX, MailPackagerMenu.CONFIG_Y - 1, maxWidth, address != null ? TEXT_COLOR : WARN_COLOR);

        Component mailboxLine = menu.isMailboxAttached()
              ? Component.translatable("gui.envelope_automate.mailbox_attached")
              : Component.translatable("gui.envelope_automate.mailbox_not_attached");
        drawClipped(graphics, mailboxLine, textX, MailPackagerMenu.CONFIG_Y + 9, maxWidth, menu.isMailboxAttached() ? OK_COLOR : WARN_COLOR);
    }

    private void drawClipped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        String string = text.getString();
        if (font.width(string) > maxWidth) {
            string = font.plainSubstrByWidth(string, maxWidth - font.width("...")) + "...";
        }
        graphics.drawString(font, string, x, y, color, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && isOverDirectButton(mouseX, mouseY)) {
            String mode = menu.isDirectMode() ? "direct" : "regular";
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.envelope_automate.mail_packager.mode." + mode).getVisualOrderText());
            lines.addAll(font.split(Component.translatable("gui.envelope_automate.mail_packager.mode." + mode + ".description")
                  .withStyle(ChatFormatting.GRAY), 200));
            lines.add(Component.translatable("gui.envelope_automate.mail_packager.mode.toggle")
                  .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            graphics.renderTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
            int index = menu.slots.indexOf(hoveredSlot);
            String key = index == MailPackagerMenu.ADDRESS_SLOT ? "address_slot"
                  : index == MailPackagerMenu.SEAL_SLOT ? "seal_slot"
                  : index == MailPackagerMenu.OUTGOING_SLOT ? "outgoing_slot"
                  : index < MailPackagerMenu.OUTPUT_START ? "input_slot"
                  : index < MailPackagerMenu.ADDRESS_SLOT ? "output_slot"
                  : null;
            if (key != null) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.envelope_automate.mail_packager." + key).getVisualOrderText());
                lines.addAll(font.split(Component.translatable("gui.envelope_automate.mail_packager." + key + ".description")
                      .withStyle(ChatFormatting.GRAY), 200));
                graphics.renderTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}

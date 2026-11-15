package io.github.envelopeautomate.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws vanilla-styled container panels and slots without a background texture.
 */
public final class GuiDraw {
    public static final int BLACK = 0xFF000000;
    public static final int BODY = 0xFFC6C6C6;
    public static final int HIGHLIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int SLOT_DARK = 0xFF373737;
    public static final int SLOT_BODY = 0xFF8B8B8B;

    private GuiDraw() {
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y, x + width - 1, y + height, BLACK);
        graphics.fill(x, y + 1, x + width, y + height - 1, BLACK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, BODY);
        graphics.fill(x + 1, y + 1, x + width - 3, y + 3, HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 3, HIGHLIGHT);
        graphics.fill(x + 3, y + height - 3, x + width - 1, y + height - 1, SHADOW);
        graphics.fill(x + width - 3, y + 3, x + width - 1, y + height - 1, SHADOW);
    }

    /**
     * Inset area (like a slot, but any size). x/y/width/height describe the inner area.
     */
    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, SLOT_BODY);
        graphics.fill(x - 1, y - 1, x + width, y, SLOT_DARK);
        graphics.fill(x - 1, y - 1, x, y + height, SLOT_DARK);
        graphics.fill(x, y + height, x + width + 1, y + height + 1, HIGHLIGHT);
        graphics.fill(x + width, y, x + width + 1, y + height + 1, HIGHLIGHT);
    }

    /**
     * Slot frame for an item rendered at x/y.
     */
    public static void slot(GuiGraphics graphics, int x, int y) {
        inset(graphics, x, y, 16, 16);
    }
}

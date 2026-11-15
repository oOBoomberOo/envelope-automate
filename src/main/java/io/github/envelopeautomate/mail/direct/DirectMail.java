package io.github.envelopeautomate.mail.direct;

import io.github.envelopeautomate.ModRegistry;
import io.github.mortuusars.envelope.util.Colors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/**
 * Direct Mail is flown straight from one mailbox to another by a {@link DirectMailPigeon}, skipping the
 * sorting facility.
 * It only reaches loaded mailboxes within {@link #CHUNK_RADIUS} chunks of the sender.
 */
public final class DirectMail {
    public static final int CHUNK_RADIUS = 8;

    public static final Component OUT_OF_RANGE = Component.translatable(
          "gui.envelope_automate.delivery_log.message.direct_mail.out_of_range", CHUNK_RADIUS).withColor(Colors.TOOLTIP_RED);
    public static final Component NOT_LOADED = Component.translatable(
          "gui.envelope_automate.delivery_log.message.direct_mail.not_loaded").withColor(Colors.TOOLTIP_RED);
    public static final Component NOT_A_MAILBOX = Component.translatable(
          "gui.envelope_automate.delivery_log.message.direct_mail.not_a_mailbox").withColor(Colors.TOOLTIP_RED);

    private DirectMail() {
    }

    public static boolean isDirect(ItemStack stack) {
        return stack.is(ModRegistry.DIRECT_LETTER.get())
              || stack.is(ModRegistry.SEALED_DIRECT_LETTER.get())
              || stack.is(ModRegistry.DIRECT_PACKAGE.get())
              || stack.is(ModRegistry.SEALED_DIRECT_PACKAGE.get());
    }

    /**
     * Chunk distance, measured as the larger of the two axis differences (a square of chunks around the sender).
     */
    public static boolean isInRange(BlockPos from, BlockPos to) {
        ChunkPos a = new ChunkPos(from);
        ChunkPos b = new ChunkPos(to);
        return Math.abs(a.x - b.x) <= CHUNK_RADIUS && Math.abs(a.z - b.z) <= CHUNK_RADIUS;
    }
}

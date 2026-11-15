package io.github.envelopeautomate.mail.direct;

import io.github.envelopeautomate.ModRegistry;
import io.github.mortuusars.envelope.Envelope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/**
 * Direct Mail is flown straight from one mailbox to another by a {@link DirectMailPigeon}, skipping the
 * sorting facility.
 * It only reaches loaded mailboxes within {@link #CHUNK_RADIUS} chunks of the sender; anything else is turned
 * back into regular mail (see {@link #toRegular}) and left to Envelope's couriers.
 */
public final class DirectMail {
    public static final int CHUNK_RADIUS = 8;

    private DirectMail() {
    }

    public static boolean isDirect(ItemStack stack) {
        return stack.is(ModRegistry.DIRECT_LETTER.get())
              || stack.is(ModRegistry.SEALED_DIRECT_LETTER.get())
              || stack.is(ModRegistry.DIRECT_PACKAGE.get())
              || stack.is(ModRegistry.SEALED_DIRECT_PACKAGE.get());
    }

    /**
     * The regular Envelope counterpart of a Direct Mail stack, with everything it carries (contents, address,
     * seal, name) kept. Stacks that aren't Direct Mail are returned as they are.
     */
    public static ItemStack toRegular(ItemStack stack) {
        if (stack.is(ModRegistry.DIRECT_LETTER.get())) {
            return stack.transmuteCopy(Envelope.Items.LETTER.get());
        }
        if (stack.is(ModRegistry.SEALED_DIRECT_LETTER.get())) {
            return stack.transmuteCopy(Envelope.Items.SEALED_LETTER.get());
        }
        if (stack.is(ModRegistry.DIRECT_PACKAGE.get())) {
            return stack.transmuteCopy(Envelope.Items.PACKAGE.get());
        }
        if (stack.is(ModRegistry.SEALED_DIRECT_PACKAGE.get())) {
            return stack.transmuteCopy(Envelope.Items.SEALED_PACKAGE.get());
        }
        return stack;
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

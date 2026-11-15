package io.github.envelopeautomate.mail;

import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A block is "attached" to a mailbox when the mailbox is directly adjacent to it (any side).
 */
public final class MailboxLink {
    private MailboxLink() {
    }

    public static @Nullable MailboxBlockEntity findAttached(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.isLoaded(neighbor) && level.getBlockEntity(neighbor) instanceof MailboxBlockEntity mailbox && !mailbox.isRemoved()) {
                return mailbox;
            }
        }
        return null;
    }

    /**
     * Puts mail into the mailbox's outgoing slot, where it waits to be picked up by a courier.
     *
     * @return true if the mail was placed. The passed stack is not modified.
     */
    public static boolean post(MailboxBlockEntity mailbox, ItemStack mail) {
        if (mail.isEmpty()
              || !mailbox.getItem(MailboxBlockEntity.SLOT_MAIL).isEmpty()
              || !mailbox.isSendable(mail)) {
            return false;
        }

        mailbox.setItem(MailboxBlockEntity.SLOT_MAIL, mail.copy());
        mailbox.setChanged();
        return true;
    }
}

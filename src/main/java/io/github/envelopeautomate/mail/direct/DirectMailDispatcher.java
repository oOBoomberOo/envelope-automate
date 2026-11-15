package io.github.envelopeautomate.mail.direct;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.block.mailbox.Mailboxes;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.Address;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Picks Direct Mail out of the outgoing slot of every loaded mailbox and sends it straight to the recipient's
 * mailbox with a {@link DirectMailPigeon}. No courier food is needed and the sorting facility is skipped.
 * Mail that can't be delivered directly is converted to regular mail and left in the outgoing slot for Envelope's
 * own couriers.
 */
public final class DirectMailDispatcher {
    private static final int INTERVAL = 10;

    private DirectMailDispatcher() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        //noinspection ConstantValue
        if (level == null || level.getGameTime() % INTERVAL != 0 || !MailService.operatesIn(level)) {
            return;
        }

        MailService service = MailService.of(level);
        Mailboxes mailboxes = service.getMailboxes();

        // Copy: the address set is a live view and mailboxes may register themselves while we look them up.
        for (BlockAddress address : List.copyOf(mailboxes.getAllAddresses())) {
            Optional<BlockPos> pos = mailboxes.getPositionOf(address);
            if (pos.isEmpty() || !level.isLoaded(pos.get())) {
                continue;
            }

            if (level.getBlockEntity(pos.get()) instanceof MailboxBlockEntity mailbox
                  && !mailbox.isRemoved()
                  && DirectMail.isDirect(mailbox.getItem(MailboxBlockEntity.SLOT_MAIL))) {
                dispatch(level, service, mailbox, address);
            }
        }
    }

    private static void dispatch(ServerLevel level, MailService service, MailboxBlockEntity mailbox, BlockAddress sender) {
        ItemStack outgoing = mailbox.getItem(MailboxBlockEntity.SLOT_MAIL);
        if (!mailbox.isSendable(outgoing)) {
            return;
        }

        BlockPos senderPos = mailbox.getBlockPos();
        Address recipient = Mail.getRecipientOrUnknown(outgoing).resolve(service);

        @Nullable BlockPos recipientPos = recipient instanceof BlockAddress recipientAddress
              ? reachableMailboxPos(service, senderPos, recipientAddress)
              : null;

        if (recipientPos == null) {
            // Not something a pigeon can fly straight to: a service or player-less address, an unknown address,
            // or a mailbox that is too far away or not loaded. Envelope knows how to deal with all of those
            // (sorting facility, services, lost mail), so hand it over as regular mail. It stays in the outgoing
            // slot and waits for a courier like any other mail.
            mailbox.setItem(MailboxBlockEntity.SLOT_MAIL, DirectMail.toRegular(outgoing));
            mailbox.setChanged();
            return;
        }

        ItemStack mail = Mail.removePreviousDeliveryData(outgoing.copyWithCount(1));
        mailbox.removeItem(MailboxBlockEntity.SLOT_MAIL, 1);

        // The pigeon's delivery writes the sender and the delivery log itself, same as a regular courier.
        DirectMailPigeon.dispatch(level, mail, mailbox.getOwner(), sender, senderPos, (BlockAddress) recipient, recipientPos);

        level.playSound(null, senderPos, Envelope.SoundEvents.COURIER_MAIL_PICKUP.get(), SoundSource.NEUTRAL, 0.8f, 1.5f);
    }

    /**
     * Position of the recipient's mailbox if a Direct Mail pigeon can deliver there right now: the mailbox is
     * registered, within range of the sender and its block entity is loaded.
     */
    private static @Nullable BlockPos reachableMailboxPos(MailService service, BlockPos senderPos, BlockAddress recipient) {
        @Nullable BlockPos pos = service.getMailboxes().getPositionOf(recipient).orElse(null);
        if (pos == null || !DirectMail.isInRange(senderPos, pos) || service.getMailboxes().getBlockEntityOf(recipient).isEmpty()) {
            return null;
        }
        return pos;
    }
}

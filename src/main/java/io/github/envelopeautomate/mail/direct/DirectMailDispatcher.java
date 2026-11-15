package io.github.envelopeautomate.mail.direct;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.block.mailbox.Mailboxes;
import io.github.mortuusars.envelope.world.item.component.mail.log.DeliveryRecord;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.Address;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Picks Direct Mail out of the outgoing slot of every loaded mailbox and sends it straight to the recipient's
 * mailbox with a {@link DirectMailPigeon}. No courier food is needed and the sorting facility is skipped.
 * Mail that can't be delivered directly goes back into the sender's inbox with the reason in its delivery log.
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

        @Nullable Component failure = null;
        @Nullable BlockPos recipientPos = null;

        if (!(recipient instanceof BlockAddress recipientAddress)) {
            failure = DirectMail.NOT_A_MAILBOX;
        } else {
            recipientPos = service.getMailboxes().getPositionOf(recipientAddress).orElse(null);
            if (recipientPos == null) {
                failure = DirectMail.NOT_A_MAILBOX;
            } else if (!DirectMail.isInRange(senderPos, recipientPos)) {
                failure = DirectMail.OUT_OF_RANGE;
            } else if (service.getMailboxes().getBlockEntityOf(recipientAddress).isEmpty()) {
                failure = DirectMail.NOT_LOADED;
            }
        }

        if (failure != null && mailbox.isInboxFull()) {
            return; // Nowhere to return it to; it stays in the outgoing slot until there is room.
        }

        ItemStack mail = Mail.removePreviousDeliveryData(outgoing.copyWithCount(1));
        mailbox.removeItem(MailboxBlockEntity.SLOT_MAIL, 1);

        if (failure != null) {
            Mail.setSender(mail, sender);
            Mail.writeToLog(mail, DeliveryRecord.sentFrom(sender));
            Mail.returned(mail, failure);
            if (mailbox.addMail(mail)) {
                mailbox.onMailInserted(mail);
            } else {
                Containers.dropItemStack(level, senderPos.getX() + 0.5, senderPos.getY() + 1, senderPos.getZ() + 0.5, mail);
            }
            return;
        }

        // The pigeon's delivery writes the sender and the delivery log itself, same as a regular courier.
        DirectMailPigeon.dispatch(level, mail, mailbox.getOwner(), sender, senderPos, (BlockAddress) recipient, recipientPos);

        level.playSound(null, senderPos, Envelope.SoundEvents.COURIER_MAIL_PICKUP.get(), SoundSource.NEUTRAL, 0.8f, 1.5f);
    }
}

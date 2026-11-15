package io.github.envelopeautomate.depository;

import com.mojang.logging.LogUtils;
import io.github.envelopeautomate.EnvelopeAutomate;
import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.stamp.StampSeals;
import io.github.mortuusars.envelope.Config;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.neoforge.api.event.HandleMailDropOffEvent;
import io.github.mortuusars.envelope.util.Colors;
import io.github.mortuusars.envelope.world.item.PackageItem;
import io.github.mortuusars.envelope.world.item.component.ServiceLetterMeaning;
import io.github.mortuusars.envelope.world.item.component.mail.log.DeliveryRecord;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.Address;
import io.github.mortuusars.envelope.world.mail.address.type.ServiceAddress;
import io.github.mortuusars.envelope.world.mail.delivery.Delivery;
import io.github.mortuusars.envelope.world.mail.dropoff.MailDropOffContext;
import io.github.mortuusars.envelope.world.mail.dropoff.MailDropOffResult;
import io.github.mortuusars.envelope.world.mail.service.cloud_depository.CloudDepository;
import io.github.mortuusars.envelope.world.mail.service.cloud_depository.CloudDepositoryData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles itemized withdrawal orders arriving at the Cloud Depository.
 * <p>
 * Envelope's own withdrawal request can only take out one whole stored mail item (the last one, or one matching
 * a custom name). Orders created by the Depository Terminal carry a {@link WithdrawalOrder} component listing exact
 * items and amounts; this handler takes them out of the account (opening stored packages as needed), packs them
 * into packages sealed by the depository and delivers them to the order's destination.
 */
public final class DepositoryWithdrawals {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ServiceLetterMeaning ORDER_MEANING =
          new ServiceLetterMeaning(EnvelopeAutomate.id("cloud_depository/withdrawal_order"));

    public static final Component RETURN_MESSAGE_NOTHING_AVAILABLE = Component.translatable(
          "gui.envelope_automate.delivery_log.message.withdrawal_order.nothing_available").withColor(Colors.TOOLTIP_RED);
    public static final Component RETURN_MESSAGE_DESTINATION_NOT_FOUND = Component.translatable(
          "gui.envelope_automate.delivery_log.message.withdrawal_order.destination_not_found").withColor(Colors.TOOLTIP_RED);

    private DepositoryWithdrawals() {
    }

    public static void onMailDropOff(HandleMailDropOffEvent event) {
        MailDropOffContext context = event.getContext();
        ItemStack mail = context.getMail();

        @Nullable WithdrawalOrder order = mail.get(ModRegistry.WITHDRAWAL_ORDER.get());
        if (order == null
              || context.isReturned()
              || !(context.getTarget() instanceof ServiceAddress target)
              || !target.getDefinitionHolder().is(ServiceAddress.CLOUD_DEPOSITORY)) {
            return;
        }

        try {
            event.setResult(handleOrder(context, order));
        } catch (Exception e) {
            LOGGER.error("Failed to handle withdrawal order {}", context.getDelivery(), e);
            event.setResult(MailDropOffResult.returned(mail, DeliveryRecord.Message.UNABLE_TO_REACH));
        }
    }

    private static MailDropOffResult handleOrder(MailDropOffContext context, WithdrawalOrder order) {
        ItemStack mail = context.getMail();
        MailService service = context.getService();

        if (!Config.Server.SERVICE_CLOUD_DEPOSITORY_ENABLED.get()) {
            return MailDropOffResult.returned(mail, DeliveryRecord.Message.UNAVAILABLE);
        }

        if (!(mail.get(Envelope.DataComponents.SEAL) instanceof Seal seal) || seal.getSignatureAsId().isEmpty()) {
            return MailDropOffResult.returned(mail, CloudDepository.RETURN_MESSAGE_NO_IDENTITY);
        }

        String account = seal.getSignatureAsId();

        if (Config.Server.SERVICE_CLOUD_DEPOSITORY_ONLY_PLAYERS.get() && service.getKnownPlayers().getDataOf(account).isEmpty()) {
            return MailDropOffResult.returned(mail, CloudDepository.RETURN_MESSAGE_NON_SERVICEABLE_IDENTITY);
        }

        CloudDepository depository = service.getCloudService();
        @Nullable CloudDepositoryData.Account accountData = depository.getData().accounts().get(account);
        if (accountData == null || accountData.getItems().isEmpty()) {
            return MailDropOffResult.returned(mail, CloudDepository.RETURN_MESSAGE_STORAGE_IS_EMPTY);
        }

        Address destination = order.destination().orElse(context.getOrigin());
        if (!service.getDeliveryManager().canDeliverTo(destination)) {
            return MailDropOffResult.returned(mail, RETURN_MESSAGE_DESTINATION_NOT_FOUND);
        }

        DepositoryStock.Withdrawal withdrawal = DepositoryStock.withdraw(accountData.getItems(), order.lines());
        if (withdrawal.isEmpty()) {
            return MailDropOffResult.returned(mail, RETURN_MESSAGE_NOTHING_AVAILABLE);
        }
        depository.getData().setDirty();

        ServiceAddress depositoryAddress = depository.getAddress();
        List<ItemStack> shipments = new ArrayList<>();

        PackageItem packageItem = Envelope.Items.PACKAGE.get();
        for (ItemStack pkg : Mail.createPackages(withdrawal.looseItems(), builder -> builder.recipient(destination))) {
            shipments.add(packageItem.seal(context.getLevel(), pkg, depository.getSeal()));
        }
        if (withdrawal.experience() > 0 && !shipments.isEmpty()) {
            shipments.getFirst().set(Envelope.DataComponents.PACKAGE_EXPERIENCE, withdrawal.experience());
        }
        shipments.addAll(withdrawal.wholeMail());

        shipments.replaceAll(stack -> Mail.of(stack)
              .sender(depositoryAddress)
              .recipient(destination)
              .writeToLog(DeliveryRecord.message(CloudDepository.LOG_MESSAGE_WITHDRAWN))
              .itemStack());

        LOGGER.debug("Withdrawal order from '{}' produced {} shipment(s) to {}", account, shipments.size(), destination);

        boolean replyToOrigin = destination.equals(context.getOrigin());
        for (int i = replyToOrigin ? 1 : 0; i < shipments.size(); i++) {
            service.getDeliveryManager().startService(Delivery.draft()
                  .deliver(shipments.get(i))
                  .from(depositoryAddress)
                  .to(destination));
        }

        return replyToOrigin ? MailDropOffResult.reply(shipments.getFirst()) : MailDropOffResult.CONSUME;
    }

    // -- Order letter

    /**
     * @param signer account owner the order is sealed for (withdrawals come out of this account).
     * @param stamp  stamp whose material/die the seal uses, or empty for a plain wax seal.
     */
    public static ItemStack createOrderLetter(ServerPlayer player, StampSeals.Signer signer, ItemStack stamp, WithdrawalOrder order) {
        MailService service = MailService.of(player.server.overworld());
        ServiceAddress depositoryAddress = service.getCloudService().getAddress();

        Seal seal = StampSeals.create(stamp, signer, player.registryAccess());

        return Mail.of(Envelope.Items.SEALED_SERVICE_LETTER)
              .name(Component.translatable("item.envelope_automate.withdrawal_order"))
              .letterContent(createLetterText(order))
              .serviceLetterMeaning(ORDER_MEANING)
              .addressTag(depositoryAddress)
              .seal(seal)
              .set(ModRegistry.WITHDRAWAL_ORDER.get(), order)
              .itemStack();
    }

    private static Component createLetterText(WithdrawalOrder order) {
        Component destination = order.destination()
              .<Component>map(address -> address.getComponent().withStyle(ChatFormatting.DARK_BLUE))
              .orElseGet(() -> Component.translatable("letter.envelope_automate.withdrawal_order.return_to_sender")
                    .withStyle(ChatFormatting.DARK_BLUE));

        MutableComponent text = Component.translatable("letter.envelope_automate.withdrawal_order.header", destination);
        for (WithdrawalOrder.Line line : order.lines()) {
            text.append("\n").append(Component.translatable("letter.envelope_automate.withdrawal_order.line",
                  Component.literal(Integer.toString(line.amount())).withStyle(ChatFormatting.DARK_RED),
                  line.item().getHoverName()));
        }
        return text;
    }

    // -- Snapshot

    public static StockSnapshot createSnapshot(ServerPlayer player, String account, ItemStack stamp, Optional<String> attachedMailbox) {
        MailService service = MailService.of(player.server.overworld());

        @Nullable CloudDepositoryData.Account accountData = service.getCloudService().getData().accounts().get(account);

        List<DepositoryStock.Entry> entries = accountData != null ? DepositoryStock.collect(accountData.getItems()) : List.of();
        int stored = accountData != null ? accountData.getTotal() : 0;
        int capacity = accountData != null
              ? accountData.getCapacity()
              : Config.Server.SERVICE_CLOUD_DEPOSITORY_ACCOUNT_STORAGE_STARTING_CAPACITY.get();

        List<String> knownAddresses = service.getKnownAddresses().stream()
              .filter(address -> !(address instanceof ServiceAddress serviceAddress) || !serviceAddress.isHidden())
              .map(Address::getString)
              .distinct()
              .sorted(String.CASE_INSENSITIVE_ORDER)
              .toList();

        return new StockSnapshot(Config.Server.SERVICE_CLOUD_DEPOSITORY_ENABLED.get(), account, stamp.copy(), stored, capacity,
              entries, attachedMailbox, knownAddresses);
    }
}

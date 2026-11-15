package io.github.envelopeautomate.client;

import io.github.envelopeautomate.EnvelopeAutomate;
import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.stamp.Fingerprint;
import io.github.mortuusars.envelope.world.item.SealStampItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

@Mod(value = EnvelopeAutomate.MOD_ID, dist = Dist.CLIENT)
public class EnvelopeAutomateClient {
    public EnvelopeAutomateClient(IEventBus modBus) {
        modBus.addListener(EnvelopeAutomateClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(EnvelopeAutomateClient::addStampTooltip);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.MAIL_PACKAGER_MENU.get(), MailPackagerScreen::new);
        event.register(ModRegistry.DEPOSITORY_TERMINAL_MENU.get(), DepositoryTerminalScreen::new);
    }

    private static void addStampTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof SealStampItem)) {
            return;
        }

        @Nullable Fingerprint fingerprint = Fingerprint.get(event.getItemStack());
        if (fingerprint != null) {
            event.getToolTip().add(1, Component.translatable("item.envelope_automate.seal_stamp.fingerprint",
                  Component.literal(fingerprint.name()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        } else {
            event.getToolTip().add(1, Component.translatable("item.envelope_automate.seal_stamp.fingerprint_hint")
                  .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

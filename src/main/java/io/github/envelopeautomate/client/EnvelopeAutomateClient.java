package io.github.envelopeautomate.client;

import io.github.envelopeautomate.EnvelopeAutomate;
import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.stamp.Fingerprint;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.renderer.entity.PigeonRenderer;
import io.github.mortuusars.envelope.world.item.SealStampItem;
import io.github.mortuusars.envelope.world.item.SealedItem;
import io.github.mortuusars.envelope.world.item.component.LetterContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

@Mod(value = EnvelopeAutomate.MOD_ID, dist = Dist.CLIENT)
public class EnvelopeAutomateClient {
    public EnvelopeAutomateClient(IEventBus modBus) {
        modBus.addListener(EnvelopeAutomateClient::registerScreens);
        modBus.addListener(EnvelopeAutomateClient::registerRenderers);
        modBus.addListener(EnvelopeAutomateClient::registerItemColors);
        modBus.addListener(EnvelopeAutomateClient::clientSetup);
        NeoForge.EVENT_BUS.addListener(EnvelopeAutomateClient::addStampTooltip);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.MAIL_PACKAGER_MENU.get(), MailPackagerScreen::new);
        event.register(ModRegistry.DEPOSITORY_TERMINAL_MENU.get(), DepositoryTerminalScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Envelope's own pigeon renderer (model layers, mail backpack and hat are registered by Envelope).
        event.registerEntityRenderer(ModRegistry.DIRECT_MAIL_PIGEON.get(), PigeonRenderer::new);
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(SealedItem.Client::getSealOverlayColor,
              ModRegistry.SEALED_DIRECT_LETTER.get(), ModRegistry.SEALED_DIRECT_PACKAGE.get());
    }

    /**
     * Same model predicates Envelope registers for its own letter and package, so the open/written textures work.
     */
    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ResourceLocation open = ResourceLocation.fromNamespaceAndPath("envelope", "open");
            ResourceLocation hasContent = ResourceLocation.fromNamespaceAndPath("envelope", "has_content");

            ItemProperties.register(ModRegistry.DIRECT_LETTER.get(), open,
                  (stack, level, entity, seed) -> stack.has(Envelope.DataComponents.OPEN) ? 1 : 0);
            ItemProperties.register(ModRegistry.DIRECT_LETTER.get(), hasContent, (stack, level, entity, seed) -> {
                @Nullable LetterContent content = stack.get(Envelope.DataComponents.LETTER_CONTENT);
                return content != null && !content.isEmpty() ? 1 : 0;
            });
            ItemProperties.register(ModRegistry.DIRECT_PACKAGE.get(), open,
                  (stack, level, entity, seed) -> stack.has(Envelope.DataComponents.OPEN) ? 1 : 0);
        });
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

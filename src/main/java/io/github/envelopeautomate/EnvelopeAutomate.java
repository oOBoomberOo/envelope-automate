package io.github.envelopeautomate;

import io.github.envelopeautomate.depository.DepositoryWithdrawals;
import io.github.envelopeautomate.network.ModPayloads;
import io.github.envelopeautomate.stamp.FingerprintEvents;
import io.github.mortuusars.envelope.Envelope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(EnvelopeAutomate.MOD_ID)
public class EnvelopeAutomate {
    public static final String MOD_ID = "envelope_automate";

    public EnvelopeAutomate(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(ModPayloads::register);
        modBus.addListener(EnvelopeAutomate::registerCapabilities);
        modBus.addListener(EnvelopeAutomate::buildCreativeTabs);

        NeoForge.EVENT_BUS.addListener(DepositoryWithdrawals::onMailDropOff);
        NeoForge.EVENT_BUS.addListener(FingerprintEvents::onRightClickItem);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModRegistry.MAIL_PACKAGER_BE.get(),
              (blockEntity, side) -> blockEntity.getAutomationHandler());
    }

    private static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == Envelope.CreativeTabs.ENVELOPE.get()
              || event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.MAIL_PACKAGER_ITEM.get());
            event.accept(ModRegistry.DEPOSITORY_TERMINAL_ITEM.get());
        }
    }
}

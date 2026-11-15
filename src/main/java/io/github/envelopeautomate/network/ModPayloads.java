package io.github.envelopeautomate.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private ModPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(ClientboundStockSnapshotPayload.TYPE, ClientboundStockSnapshotPayload.STREAM_CODEC,
              ClientboundStockSnapshotPayload::handle);
        registrar.playToServer(ServerboundTakeStampPayload.TYPE, ServerboundTakeStampPayload.STREAM_CODEC,
              ServerboundTakeStampPayload::handle);
        registrar.playToServer(ServerboundSubmitOrderPayload.TYPE, ServerboundSubmitOrderPayload.STREAM_CODEC,
              ServerboundSubmitOrderPayload::handle);
    }
}

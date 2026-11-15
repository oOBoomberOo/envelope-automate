package io.github.envelopeautomate.network;

import io.github.envelopeautomate.EnvelopeAutomate;
import io.github.envelopeautomate.depository.StockSnapshot;
import io.github.envelopeautomate.menu.DepositoryTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ClientboundStockSnapshotPayload(StockSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ClientboundStockSnapshotPayload> TYPE = new Type<>(EnvelopeAutomate.id("stock_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundStockSnapshotPayload> STREAM_CODEC =
          StockSnapshot.STREAM_CODEC.map(ClientboundStockSnapshotPayload::new, ClientboundStockSnapshotPayload::snapshot);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if (context.player().containerMenu instanceof DepositoryTerminalMenu menu) {
            menu.setSnapshot(snapshot);
        }
    }
}

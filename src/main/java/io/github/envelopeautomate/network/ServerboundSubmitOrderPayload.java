package io.github.envelopeautomate.network;

import io.github.envelopeautomate.EnvelopeAutomate;
import io.github.envelopeautomate.depository.WithdrawalOrder;
import io.github.envelopeautomate.menu.DepositoryTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ServerboundSubmitOrderPayload(List<WithdrawalOrder.Line> lines, String destination) implements CustomPacketPayload {
    public static final Type<ServerboundSubmitOrderPayload> TYPE = new Type<>(EnvelopeAutomate.id("submit_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSubmitOrderPayload> STREAM_CODEC = StreamCodec.composite(
          WithdrawalOrder.Line.STREAM_CODEC.apply(ByteBufCodecs.list()), ServerboundSubmitOrderPayload::lines,
          ByteBufCodecs.stringUtf8(256), ServerboundSubmitOrderPayload::destination,
          ServerboundSubmitOrderPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
              && player.containerMenu instanceof DepositoryTerminalMenu menu
              && menu.stillValid(player)) {
            menu.submitOrder(player, lines, destination);
        }
    }
}

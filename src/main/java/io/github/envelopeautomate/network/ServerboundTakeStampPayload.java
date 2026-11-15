package io.github.envelopeautomate.network;

import io.github.envelopeautomate.EnvelopeAutomate;
import io.github.envelopeautomate.menu.DepositoryTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Takes the inserted seal stamp out of the Depository Terminal the player has open.
 */
public record ServerboundTakeStampPayload() implements CustomPacketPayload {
    public static final ServerboundTakeStampPayload INSTANCE = new ServerboundTakeStampPayload();
    public static final Type<ServerboundTakeStampPayload> TYPE = new Type<>(EnvelopeAutomate.id("take_stamp"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundTakeStampPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
              && player.containerMenu instanceof DepositoryTerminalMenu menu
              && menu.stillValid(player)) {
            menu.takeStamp(player);
        }
    }
}

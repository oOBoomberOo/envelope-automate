package io.github.envelopeautomate.stamp;

import io.github.envelopeautomate.ModRegistry;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.SealStampItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public final class FingerprintEvents {
    private FingerprintEvents() {
    }

    /**
     * Sneak + use a seal stamp: record your fingerprint on it. Doing it again on your own fingerprint wipes it.
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!(stack.getItem() instanceof SealStampItem) || !player.isSecondaryUseActive()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));

        if (event.getLevel().isClientSide()) {
            return;
        }

        @Nullable Fingerprint existing = Fingerprint.get(stack);
        if (existing != null && existing.belongsTo(player)) {
            stack.remove(ModRegistry.FINGERPRINT.get());
            player.displayClientMessage(Component.translatable("message.envelope_automate.fingerprint.removed"), true);
            event.getLevel().playSound(null, player, Envelope.SoundEvents.UI_WRITE.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
        } else {
            stack.set(ModRegistry.FINGERPRINT.get(), Fingerprint.of(player));
            player.displayClientMessage(Component.translatable("message.envelope_automate.fingerprint.recorded",
                  Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.AQUA)), true);
            event.getLevel().playSound(null, player, Envelope.SoundEvents.SEAL_STAMP_APPLY.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }
}

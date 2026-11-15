package io.github.envelopeautomate.stamp;

import io.github.mortuusars.envelope.world.item.SealStampItem;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import io.github.mortuusars.envelope.world.item.component.seal.SealSymbol;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Builds seals the way Envelope's seal stamp does, but for a signer that isn't necessarily the player holding it.
 */
public final class StampSeals {
    private StampSeals() {
    }

    public record Signer(String name, @Nullable UUID id) {
    }

    /**
     * The stamp's fingerprint if it has one, otherwise the fallback signer.
     */
    public static Signer signerOf(ItemStack stamp, MinecraftServer server, Signer fallback) {
        @Nullable Fingerprint fingerprint = Fingerprint.get(stamp);
        return fingerprint != null ? new Signer(fingerprint.resolveName(server), fingerprint.id()) : fallback;
    }

    /**
     * @param stamp seal stamp providing material and die, or empty for a plain wax seal.
     */
    public static Seal create(ItemStack stamp, Signer signer, RegistryAccess registries) {
        Seal.Builder builder = Seal.create(registries)
              .signature(signer.name().isEmpty() ? null : Component.literal(signer.name()))
              .playerUuid(signer.id());

        Optional<Holder<SealSymbol>> die = Optional.empty();
        if (stamp.getItem() instanceof SealStampItem stampItem) {
            builder.material(stampItem.getMaterialOrDefault(stamp, registries));
            die = stampItem.getDie(stamp).flatMap(either -> either.unwrap(registries));
        }

        if (die.isPresent()) {
            builder.impression(die.get());
        } else {
            builder.impression(SealSymbol.firstCharOrDefault(signer.name()));
        }

        return builder.build();
    }
}

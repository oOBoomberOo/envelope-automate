package io.github.envelopeautomate.stamp;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.envelopeautomate.ModRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A player's fingerprint recorded on a seal stamp (sneak + use). Machines using the stamp seal on behalf of
 * this player, and the Depository Terminal shows this player's Cloud Depository account.
 *
 * @param name the player's name when recorded. Use {@link #resolveName} to follow later renames.
 */
public record Fingerprint(UUID id, String name) {
    public static final Codec<Fingerprint> CODEC = RecordCodecBuilder.create(i -> i.group(
          UUIDUtil.CODEC.fieldOf("id").forGetter(Fingerprint::id),
          Codec.STRING.fieldOf("name").forGetter(Fingerprint::name)
    ).apply(i, Fingerprint::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Fingerprint> STREAM_CODEC = StreamCodec.composite(
          UUIDUtil.STREAM_CODEC, Fingerprint::id,
          ByteBufCodecs.STRING_UTF8, Fingerprint::name,
          Fingerprint::new
    );

    public static Fingerprint of(Player player) {
        return new Fingerprint(player.getUUID(), player.getScoreboardName());
    }

    public static @Nullable Fingerprint get(ItemStack stack) {
        return stack.get(ModRegistry.FINGERPRINT.get());
    }

    public boolean belongsTo(Player player) {
        return id.equals(player.getUUID());
    }

    /**
     * Current name of the player: Envelope keys depository accounts and seal signatures by name,
     * so a renamed player should keep working with stamps fingerprinted before the rename.
     */
    public String resolveName(MinecraftServer server) {
        @Nullable ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getScoreboardName();
        }
        @Nullable GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            return cache.get(id).map(GameProfile::getName).orElse(name);
        }
        return name;
    }
}

package io.github.envelopeautomate.depository;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * What the Depository Terminal shows: a Cloud Depository account.
 *
 * @param account         account name (the viewer's, or the inserted stamp's fingerprint).
 * @param stamp           seal stamp inserted into the terminal, or empty.
 * @param attachedMailbox address of the mailbox attached to the terminal, if any.
 * @param knownAddresses  addresses for destination suggestions.
 */
public record StockSnapshot(boolean serviceEnabled, String account, ItemStack stamp, int storedMail, int capacity,
                            List<DepositoryStock.Entry> entries, Optional<String> attachedMailbox,
                            List<String> knownAddresses) {
    /**
     * Value comparison (ItemStack has no value equality, so records can't do this themselves).
     */
    public boolean sameAs(StockSnapshot other) {
        if (serviceEnabled != other.serviceEnabled
              || storedMail != other.storedMail
              || capacity != other.capacity
              || !account.equals(other.account)
              || !ItemStack.matches(stamp, other.stamp)
              || !attachedMailbox.equals(other.attachedMailbox)
              || !knownAddresses.equals(other.knownAddresses)
              || entries.size() != other.entries.size()) {
            return false;
        }

        for (int i = 0; i < entries.size(); i++) {
            DepositoryStock.Entry a = entries.get(i);
            DepositoryStock.Entry b = other.entries.get(i);
            if (a.count() != b.count() || a.whole() != b.whole() || !ItemStack.isSameItemSameComponents(a.item(), b.item())) {
                return false;
            }
        }
        return true;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, StockSnapshot> STREAM_CODEC = StreamCodec.of(
          (buffer, snapshot) -> {
              buffer.writeBoolean(snapshot.serviceEnabled());
              buffer.writeUtf(snapshot.account());
              ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.stamp());
              buffer.writeVarInt(snapshot.storedMail());
              buffer.writeVarInt(snapshot.capacity());
              DepositoryStock.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, snapshot.entries());
              ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buffer, snapshot.attachedMailbox());
              ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buffer, snapshot.knownAddresses());
          },
          buffer -> new StockSnapshot(
                buffer.readBoolean(),
                buffer.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readVarInt(),
                buffer.readVarInt(),
                DepositoryStock.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buffer),
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buffer))
    );
}

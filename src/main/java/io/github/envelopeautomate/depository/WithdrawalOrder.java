package io.github.envelopeautomate.depository;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mortuusars.envelope.world.mail.address.Address;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Data component on a withdrawal order letter: which items to take out of the sender's Cloud Depository account,
 * and where to deliver them. Empty destination means "back to the mailbox the order was sent from".
 */
public record WithdrawalOrder(List<Line> lines, Optional<Address> destination) {
    public static final Codec<WithdrawalOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
          Line.CODEC.listOf().fieldOf("lines").forGetter(WithdrawalOrder::lines),
          Address.CODEC.optionalFieldOf("destination").forGetter(WithdrawalOrder::destination)
    ).apply(i, WithdrawalOrder::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawalOrder> STREAM_CODEC = StreamCodec.composite(
          Line.STREAM_CODEC.apply(ByteBufCodecs.list()), WithdrawalOrder::lines,
          ByteBufCodecs.optional(Address.STREAM_CODEC), WithdrawalOrder::destination,
          WithdrawalOrder::new
    );

    public WithdrawalOrder {
        lines = List.copyOf(lines);
    }

    /**
     * @param item   template stack (count is ignored).
     * @param amount number of items requested.
     * @param whole  true if the item is stored in the depository as a whole mail item (letter, unopened loot package, etc.)
     *               rather than inside a package.
     */
    public record Line(ItemStack item, int amount, boolean whole) {
        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
              ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(Line::item),
              ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(Line::amount),
              Codec.BOOL.optionalFieldOf("whole", false).forGetter(Line::whole)
        ).apply(i, Line::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
              ItemStack.STREAM_CODEC, Line::item,
              ByteBufCodecs.VAR_INT, Line::amount,
              ByteBufCodecs.BOOL, Line::whole,
              Line::new
        );

        public Line {
            item = item.copyWithCount(1);
        }

        public boolean matches(ItemStack stack, boolean whole) {
            return this.whole == whole && ItemStack.isSameItemSameComponents(item, stack);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof Line other
                  && amount == other.amount
                  && whole == other.whole
                  && ItemStack.isSameItemSameComponents(item, other.item));
        }

        @Override
        public int hashCode() {
            return Objects.hash(ItemStack.hashItemAndComponents(item), amount, whole);
        }
    }
}

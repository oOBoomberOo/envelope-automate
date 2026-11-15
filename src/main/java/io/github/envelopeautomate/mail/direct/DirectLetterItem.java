package io.github.envelopeautomate.mail.direct;

import io.github.envelopeautomate.ModRegistry;
import io.github.mortuusars.envelope.world.item.LetterItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A letter that is delivered point-to-point by {@link DirectMailDispatcher} instead of Envelope's couriers.
 * Shares Envelope's letter block, so it must not take over the block -> item mapping.
 */
public class DirectLetterItem extends LetterItem {
    public DirectLetterItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        // Envelope's own item stays the item form of the shared block.
    }

    @Override
    public @NotNull String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    @Override
    public ItemLike getSealedItem() {
        return ModRegistry.SEALED_DIRECT_LETTER.get();
    }
}

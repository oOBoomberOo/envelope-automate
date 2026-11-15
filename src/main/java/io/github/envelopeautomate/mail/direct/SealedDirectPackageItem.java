package io.github.envelopeautomate.mail.direct;

import io.github.envelopeautomate.ModRegistry;
import io.github.mortuusars.envelope.world.item.SealedPackageItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class SealedDirectPackageItem extends SealedPackageItem {
    public SealedDirectPackageItem(Block block, Properties properties) {
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
    public ItemLike getUnsealedItem() {
        return ModRegistry.DIRECT_PACKAGE.get();
    }
}

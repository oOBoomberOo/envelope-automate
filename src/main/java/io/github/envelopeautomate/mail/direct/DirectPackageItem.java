package io.github.envelopeautomate.mail.direct;

import io.github.envelopeautomate.ModRegistry;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.PackageItem;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A package that is delivered point-to-point by {@link DirectMailDispatcher} instead of Envelope's couriers.
 * Shares Envelope's package block, so it must not take over the block -> item mapping.
 */
public class DirectPackageItem extends PackageItem {
    public DirectPackageItem(Block block, Properties properties) {
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
    public ItemStack seal(Level level, ItemStack stack, Seal seal) {
        ItemStack sealed = stack.transmuteCopy(ModRegistry.SEALED_DIRECT_PACKAGE.get());
        sealed.set(Envelope.DataComponents.SEAL, seal);
        return sealed;
    }
}

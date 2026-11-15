package io.github.envelopeautomate;

import io.github.envelopeautomate.block.DepositoryTerminalBlock;
import io.github.envelopeautomate.block.DepositoryTerminalBlockEntity;
import io.github.envelopeautomate.block.MailPackagerBlock;
import io.github.envelopeautomate.block.MailPackagerBlockEntity;
import io.github.envelopeautomate.depository.WithdrawalOrder;
import io.github.envelopeautomate.menu.DepositoryTerminalMenu;
import io.github.envelopeautomate.menu.MailPackagerMenu;
import io.github.envelopeautomate.stamp.Fingerprint;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EnvelopeAutomate.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EnvelopeAutomate.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
          DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EnvelopeAutomate.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
          DeferredRegister.create(Registries.MENU, EnvelopeAutomate.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
          DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EnvelopeAutomate.MOD_ID);

    // -- Blocks

    public static final DeferredBlock<MailPackagerBlock> MAIL_PACKAGER = BLOCKS.register("mail_packager",
          () -> new MailPackagerBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                .sound(SoundType.WOOD)));

    public static final DeferredBlock<DepositoryTerminalBlock> DEPOSITORY_TERMINAL = BLOCKS.register("depository_terminal",
          () -> new DepositoryTerminalBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                .sound(SoundType.WOOD)));

    // -- Items

    public static final DeferredItem<BlockItem> MAIL_PACKAGER_ITEM =
          ITEMS.registerSimpleBlockItem(MAIL_PACKAGER, new Item.Properties());
    public static final DeferredItem<BlockItem> DEPOSITORY_TERMINAL_ITEM =
          ITEMS.registerSimpleBlockItem(DEPOSITORY_TERMINAL, new Item.Properties());

    // -- Block entities

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MailPackagerBlockEntity>> MAIL_PACKAGER_BE =
          BLOCK_ENTITIES.register("mail_packager", () -> BlockEntityType.Builder
                .of(MailPackagerBlockEntity::new, MAIL_PACKAGER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepositoryTerminalBlockEntity>> DEPOSITORY_TERMINAL_BE =
          BLOCK_ENTITIES.register("depository_terminal", () -> BlockEntityType.Builder
                .of(DepositoryTerminalBlockEntity::new, DEPOSITORY_TERMINAL.get()).build(null));

    // -- Menus

    public static final DeferredHolder<MenuType<?>, MenuType<MailPackagerMenu>> MAIL_PACKAGER_MENU =
          MENUS.register("mail_packager", () -> IMenuTypeExtension.create(MailPackagerMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<DepositoryTerminalMenu>> DEPOSITORY_TERMINAL_MENU =
          MENUS.register("depository_terminal", () -> IMenuTypeExtension.create(DepositoryTerminalMenu::fromNetwork));

    // -- Data components

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WithdrawalOrder>> WITHDRAWAL_ORDER =
          COMPONENTS.register("withdrawal_order", () -> DataComponentType.<WithdrawalOrder>builder()
                .persistent(WithdrawalOrder.CODEC)
                .networkSynchronized(WithdrawalOrder.STREAM_CODEC)
                .cacheEncoding()
                .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Fingerprint>> FINGERPRINT =
          COMPONENTS.register("fingerprint", () -> DataComponentType.<Fingerprint>builder()
                .persistent(Fingerprint.CODEC)
                .networkSynchronized(Fingerprint.STREAM_CODEC)
                .build());

    private ModRegistry() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
    }
}

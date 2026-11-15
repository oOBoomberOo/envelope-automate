package io.github.envelopeautomate.block;

import com.mojang.serialization.MapCodec;
import io.github.envelopeautomate.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.SealStampItem;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Browses the player's Cloud Depository account and creates itemized withdrawal orders.
 * When attached to a mailbox, created orders are posted to it automatically.
 */
public class DepositoryTerminalBlock extends BaseEntityBlock {
    public static final MapCodec<DepositoryTerminalBlock> CODEC = simpleCodec(DepositoryTerminalBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public DepositoryTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected @NotNull RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepositoryTerminalBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.DEPOSITORY_TERMINAL_BE.get(),
              (lvl, pos, st, blockEntity) -> blockEntity.serverTick());
    }

    /**
     * Using a seal stamp on the terminal inserts it (swapping out the previous one) and opens the terminal.
     */
    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                       InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof SealStampItem) || !(level.getBlockEntity(pos) instanceof DepositoryTerminalBlockEntity blockEntity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack previous = blockEntity.swapStamp(stack);
            stack.consume(1, player);
            giveOrDrop(player, previous);
            level.playSound(null, pos, Envelope.SoundEvents.SEAL_STAMP_APPLY.get(), SoundSource.BLOCKS, 0.7f, 0.9f);
            blockEntity.openMenu(serverPlayer);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * Sneak + use with an empty hand takes the stamp out; otherwise opens the terminal.
     */
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof DepositoryTerminalBlockEntity blockEntity)) {
            return InteractionResult.PASS;
        }

        if (player.isSecondaryUseActive() && !blockEntity.getStamp().isEmpty()) {
            if (!level.isClientSide()) {
                giveOrDrop(player, blockEntity.removeStamp());
                level.playSound(null, pos, Envelope.SoundEvents.UI_WRITE.get(), SoundSource.BLOCKS, 0.6f, 0.8f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (player instanceof ServerPlayer serverPlayer) {
            blockEntity.openMenu(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public static void giveOrDrop(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DepositoryTerminalBlockEntity blockEntity) {
            blockEntity.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

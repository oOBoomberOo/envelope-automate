package io.github.envelopeautomate.menu;

import io.github.envelopeautomate.ModRegistry;
import io.github.envelopeautomate.block.DepositoryTerminalBlock;
import io.github.envelopeautomate.block.DepositoryTerminalBlockEntity;
import io.github.envelopeautomate.depository.DepositoryWithdrawals;
import io.github.envelopeautomate.depository.StockSnapshot;
import io.github.envelopeautomate.depository.WithdrawalOrder;
import io.github.envelopeautomate.network.ClientboundStockSnapshotPayload;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.Address;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DepositoryTerminalMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerLevelAccess access;

    /**
     * How often (in ticks) the server re-checks the account for changes while the terminal is open.
     */
    private static final int SYNC_INTERVAL = 10;

    private final @Nullable ServerPlayer serverPlayer;
    private int syncTicks;

    /**
     * Client: last received snapshot. Server: last sent snapshot.
     */
    private @Nullable StockSnapshot snapshot;
    private int snapshotVersion;

    public DepositoryTerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModRegistry.DEPOSITORY_TERMINAL_MENU.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.serverPlayer = inventory.player instanceof ServerPlayer player ? player : null;
    }

    public static DepositoryTerminalMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return new DepositoryTerminalMenu(containerId, inventory, buffer.readBlockPos());
    }

    public BlockPos getPos() {
        return pos;
    }

    // -- Client-side snapshot

    public @Nullable StockSnapshot getSnapshot() {
        return snapshot;
    }

    public int getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshot(StockSnapshot snapshot) {
        this.snapshot = snapshot;
        this.snapshotVersion++;
    }

    // -- Server-side handling

    /**
     * Called every server tick for the open menu. Rebuilds the snapshot periodically and only sends it when
     * something changed, so the terminal stays live no matter what modified the depository account
     * (deposits, withdrawals, commands, other mods).
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (serverPlayer != null && (snapshot == null || ++syncTicks >= SYNC_INTERVAL)) {
            syncTicks = 0;
            syncSnapshot(serverPlayer);
        }
    }

    public void takeStamp(ServerPlayer player) {
        if (player.level().getBlockEntity(pos) instanceof DepositoryTerminalBlockEntity terminal && !terminal.getStamp().isEmpty()) {
            DepositoryTerminalBlock.giveOrDrop(player, terminal.removeStamp());
            syncSnapshot(player);
        }
    }

    private void syncSnapshot(ServerPlayer player) {
        if (!(player.level().getBlockEntity(pos) instanceof DepositoryTerminalBlockEntity terminal)) {
            return;
        }

        StockSnapshot current = DepositoryWithdrawals.createSnapshot(player, terminal.getSignerFor(player).name(),
              terminal.getStamp(), terminal.getAttachedMailboxAddress());
        if (snapshot == null || !snapshot.sameAs(current)) {
            snapshot = current;
            PacketDistributor.sendToPlayer(player, new ClientboundStockSnapshotPayload(current));
        }
    }

    public void submitOrder(ServerPlayer player, List<WithdrawalOrder.Line> requestedLines, String destinationName) {
        if (!(player.level().getBlockEntity(pos) instanceof DepositoryTerminalBlockEntity terminal)) {
            return;
        }

        List<WithdrawalOrder.Line> lines = mergeLines(requestedLines);
        if (lines.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.envelope_automate.withdrawal_order.empty")
                  .withStyle(ChatFormatting.RED), true);
            return;
        }

        Optional<Address> destination = Optional.empty();
        String name = destinationName.trim();
        if (!name.isEmpty()) {
            destination = MailService.of(player.server.overworld()).getKnownAddresses().byName(name);
            if (destination.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.envelope_automate.withdrawal_order.unknown_address", name)
                      .withStyle(ChatFormatting.RED), false);
                return;
            }
        }

        ItemStack letter = DepositoryWithdrawals.createOrderLetter(player, terminal.getSignerFor(player), terminal.getStamp(),
              new WithdrawalOrder(lines, destination));

        Optional<String> mailbox = terminal.getAttachedMailboxAddress();
        if (mailbox.isPresent()) {
            terminal.post(letter);
        } else {
            if (!player.getInventory().add(letter)) {
                player.drop(letter, false);
            }
        }
    }

    private static List<WithdrawalOrder.Line> mergeLines(List<WithdrawalOrder.Line> requested) {
        List<WithdrawalOrder.Line> merged = new ArrayList<>();
        for (WithdrawalOrder.Line line : requested) {
            if (line.item().isEmpty() || line.amount() <= 0) continue;

            boolean found = false;
            for (int i = 0; i < merged.size(); i++) {
                WithdrawalOrder.Line existing = merged.get(i);
                if (existing.matches(line.item(), line.whole())) {
                    long sum = (long) existing.amount() + line.amount();
                    merged.set(i, new WithdrawalOrder.Line(existing.item(), (int) Math.min(Integer.MAX_VALUE, sum), existing.whole()));
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(line);
            }
        }
        return merged;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModRegistry.DEPOSITORY_TERMINAL.get());
    }
}

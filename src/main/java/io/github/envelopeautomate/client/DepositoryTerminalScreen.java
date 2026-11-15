package io.github.envelopeautomate.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.envelopeautomate.depository.DepositoryStock;
import io.github.envelopeautomate.depository.StockSnapshot;
import io.github.envelopeautomate.depository.WithdrawalOrder;
import io.github.envelopeautomate.menu.DepositoryTerminalMenu;
import io.github.envelopeautomate.network.ServerboundSubmitOrderPayload;
import io.github.envelopeautomate.network.ServerboundTakeStampPayload;
import io.github.envelopeautomate.stamp.Fingerprint;
import io.github.mortuusars.envelope.Envelope;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Stock-ticker style browser for the player's Cloud Depository account.
 * Click items to add them to the order, then send it as a withdrawal order.
 */
public class DepositoryTerminalScreen extends AbstractContainerScreen<DepositoryTerminalMenu> {
    private static final int WIDTH = 194;
    private static final int HEIGHT = 230;

    private static final int COLUMNS = 9;
    private static final int GRID_ROWS = 5;
    private static final int ORDER_ROWS = 2;
    private static final int CELL = 18;

    private static final int GRID_X = 8;
    private static final int GRID_Y = 36;
    private static final int SCROLLBAR_X = 175;
    private static final int SCROLLBAR_WIDTH = 10;
    private static final int ORDER_LABEL_Y = GRID_Y + GRID_ROWS * CELL + 4;
    private static final int ORDER_Y = ORDER_LABEL_Y + 10;
    private static final int DESTINATION_LABEL_Y = ORDER_Y + ORDER_ROWS * CELL + 4;
    private static final int DESTINATION_Y = DESTINATION_LABEL_Y + 10;
    private static final int BUTTONS_Y = DESTINATION_Y + 19;
    private static final int SORT_BUTTON_WIDTH = 58;
    private static final int SORT_BUTTON_X = SCROLLBAR_X + SCROLLBAR_WIDTH - SORT_BUTTON_WIDTH;
    private static final int STAMP_X = SORT_BUTTON_X - 20;
    private static final int STAMP_Y = 17;
    private static final int FINGERPRINT_COLOR = 0x1E6B8A;

    public enum SortMode {
        QUANTITY, NAME, MOD;

        public Component label() {
            return Component.translatable("gui.envelope_automate.depository_terminal.sort." + name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Remembered for the rest of the game session.
     */
    private static SortMode sortMode = SortMode.QUANTITY;

    private static final int TEXT_COLOR = 0x404040;
    private static final int WARN_COLOR = 0xB71C1C;
    private static final int ORDERED_BACKGROUND = 0xFF6FA86F;

    private final ItemStack stampIcon = new ItemStack(Envelope.Items.SEAL_STAMP.get());

    private EditBox searchBox;
    private EditBox destinationBox;
    private Button sendButton;

    private int seenSnapshotVersion = -1;
    private boolean destinationPrefilled = false;
    private List<DepositoryStock.Entry> allEntries = List.of();
    private List<DepositoryStock.Entry> visibleEntries = List.of();
    private final List<OrderLine> order = new ArrayList<>();
    private int scrollRow = 0;
    private boolean draggingScrollbar = false;

    public DepositoryTerminalScreen(DepositoryTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        String search = searchBox != null ? searchBox.getValue() : "";
        String destination = destinationBox != null ? destinationBox.getValue() : "";

        searchBox = new EditBox(font, leftPos + GRID_X, topPos + 18, STAMP_X - 5 - GRID_X, 14,
              Component.translatable("gui.envelope_automate.depository_terminal.search"));
        searchBox.setHint(Component.translatable("gui.envelope_automate.depository_terminal.search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(64);
        searchBox.setValue(search);
        searchBox.setResponder(text -> {
            scrollRow = 0;
            applyFilter();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(CycleButton.builder(SortMode::label)
              .withValues(SortMode.values())
              .withInitialValue(sortMode)
              .displayOnlyValue()
              .withTooltip(mode -> Tooltip.create(Component.translatable("gui.envelope_automate.depository_terminal.sort")))
              .create(leftPos + SORT_BUTTON_X, topPos + 17, SORT_BUTTON_WIDTH, 16,
                    Component.translatable("gui.envelope_automate.depository_terminal.sort"),
                    (button, mode) -> {
                        sortMode = mode;
                        sortEntries();
                        applyFilter();
                    }));

        destinationBox = new EditBox(font, leftPos + GRID_X, topPos + DESTINATION_Y, 120, 14,
              Component.translatable("gui.envelope_automate.depository_terminal.destination"));
        destinationBox.setMaxLength(64);
        destinationBox.setValue(destination);
        destinationBox.setResponder(text -> updateDestinationSuggestion());
        addRenderableWidget(destinationBox);

        sendButton = addRenderableWidget(Button.builder(Component.translatable("gui.envelope_automate.depository_terminal.send"),
                    button -> sendOrder())
              .bounds(leftPos + GRID_X + 124, topPos + DESTINATION_Y - 1, SCROLLBAR_X + SCROLLBAR_WIDTH - GRID_X - 124, 16)
              .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.envelope_automate.depository_terminal.clear"),
                    button -> order.clear())
              .bounds(leftPos + GRID_X, topPos + BUTTONS_Y, 60, 16)
              .build());

        updateDestinationHint();
        updateDestinationSuggestion();
    }

    // -- Snapshot

    @Override
    protected void containerTick() {
        super.containerTick();

        if (menu.getSnapshotVersion() != seenSnapshotVersion) {
            seenSnapshotVersion = menu.getSnapshotVersion();
            applySnapshot(menu.getSnapshot());
        }

        @Nullable StockSnapshot snapshot = menu.getSnapshot();
        sendButton.active = snapshot != null && snapshot.serviceEnabled() && !order.isEmpty();
    }

    private void applySnapshot(@Nullable StockSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        allEntries = snapshot.entries();
        sortEntries();

        // Keep the order consistent with the new stock
        order.removeIf(line -> {
            @Nullable DepositoryStock.Entry entry = findEntry(line.item, line.whole);
            if (entry == null) return true;
            line.amount = Math.min(line.amount, entry.count());
            return line.amount <= 0;
        });

        if (!destinationPrefilled && destinationBox.getValue().isEmpty() && snapshot.attachedMailbox().isPresent()) {
            destinationBox.setValue(snapshot.attachedMailbox().get());
        }
        destinationPrefilled = true;

        applyFilter();
        updateDestinationHint();
    }

    private ItemStack getInsertedStamp() {
        @Nullable StockSnapshot snapshot = menu.getSnapshot();
        return snapshot != null ? snapshot.stamp() : ItemStack.EMPTY;
    }

    private boolean isOverStamp(double mouseX, double mouseY) {
        return mouseX >= leftPos + STAMP_X - 1 && mouseX < leftPos + STAMP_X + 17
              && mouseY >= topPos + STAMP_Y - 1 && mouseY < topPos + STAMP_Y + 17;
    }

    private void sortEntries() {
        Comparator<DepositoryStock.Entry> byName = Comparator.comparing(
              (DepositoryStock.Entry entry) -> entry.item().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<DepositoryStock.Entry> comparator = switch (sortMode) {
            case QUANTITY -> Comparator.comparingInt(DepositoryStock.Entry::count).reversed().thenComparing(byName);
            case NAME -> byName;
            case MOD -> Comparator.comparing((DepositoryStock.Entry entry) ->
                  BuiltInRegistries.ITEM.getKey(entry.item().getItem()).getNamespace()).thenComparing(byName);
        };
        allEntries = allEntries.stream()
              .sorted(comparator.thenComparing(DepositoryStock.Entry::whole))
              .toList();
    }

    private void applyFilter() {
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            visibleEntries = allEntries;
        } else if (query.startsWith("@")) {
            String namespace = query.substring(1);
            visibleEntries = allEntries.stream()
                  .filter(entry -> BuiltInRegistries.ITEM.getKey(entry.item().getItem()).getNamespace().startsWith(namespace))
                  .toList();
        } else {
            visibleEntries = allEntries.stream()
                  .filter(entry -> entry.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
                  .toList();
        }
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
    }

    private int maxScrollRow() {
        int rows = (visibleEntries.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - GRID_ROWS);
    }

    private void updateDestinationHint() {
        @Nullable StockSnapshot snapshot = menu.getSnapshot();
        boolean attached = snapshot != null && snapshot.attachedMailbox().isPresent();
        destinationBox.setHint(Component.translatable(attached
              ? "gui.envelope_automate.depository_terminal.destination.hint_attached"
              : "gui.envelope_automate.depository_terminal.destination.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void updateDestinationSuggestion() {
        String value = destinationBox.getValue();
        @Nullable StockSnapshot snapshot = menu.getSnapshot();
        String suggestion = null;
        if (snapshot != null && !value.isEmpty() && destinationBox.getCursorPosition() == value.length()) {
            String lower = value.toLowerCase(Locale.ROOT);
            for (String address : snapshot.knownAddresses()) {
                if (address.length() > value.length() && address.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    suggestion = address.substring(value.length());
                    break;
                }
            }
        }
        destinationBox.setSuggestion(suggestion);
    }

    private Optional<String> getSuggestedCompletion() {
        String value = destinationBox.getValue();
        @Nullable StockSnapshot snapshot = menu.getSnapshot();
        if (snapshot == null || value.isEmpty()) return Optional.empty();
        String lower = value.toLowerCase(Locale.ROOT);
        return snapshot.knownAddresses().stream()
              .filter(address -> address.length() > value.length() && address.toLowerCase(Locale.ROOT).startsWith(lower))
              .findFirst();
    }

    // -- Order

    private @Nullable DepositoryStock.Entry findEntry(ItemStack item, boolean whole) {
        for (DepositoryStock.Entry entry : allEntries) {
            if (entry.whole() == whole && ItemStack.isSameItemSameComponents(entry.item(), item)) {
                return entry;
            }
        }
        return null;
    }

    private @Nullable OrderLine findLine(DepositoryStock.Entry entry) {
        for (OrderLine line : order) {
            if (line.whole == entry.whole() && ItemStack.isSameItemSameComponents(line.item, entry.item())) {
                return line;
            }
        }
        return null;
    }

    private int getOrdered(DepositoryStock.Entry entry) {
        @Nullable OrderLine line = findLine(entry);
        return line != null ? line.amount : 0;
    }

    private void changeOrder(DepositoryStock.Entry entry, int delta, boolean setMax) {
        @Nullable OrderLine line = findLine(entry);
        int current = line != null ? line.amount : 0;
        int target = setMax ? entry.count() : Mth.clamp(current + delta, 0, entry.count());

        if (target == current) {
            return;
        }

        if (line == null) {
            order.add(new OrderLine(entry.item(), target, entry.whole()));
        } else if (target <= 0) {
            order.remove(line);
        } else {
            line.amount = target;
        }
    }

    private void sendOrder() {
        if (order.isEmpty()) {
            return;
        }
        List<WithdrawalOrder.Line> lines = order.stream()
              .map(line -> new WithdrawalOrder.Line(line.item, line.amount, line.whole))
              .toList();
        PacketDistributor.sendToServer(new ServerboundSubmitOrderPayload(lines, destinationBox.getValue().trim()));
        onClose();
    }

    // -- Rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiDraw.panel(graphics, leftPos, topPos, imageWidth, imageHeight);

        // Inserted seal stamp
        int stampX = leftPos + STAMP_X;
        int stampY = topPos + STAMP_Y;
        GuiDraw.slot(graphics, stampX, stampY);
        ItemStack stamp = getInsertedStamp();
        if (!stamp.isEmpty()) {
            graphics.renderItem(stamp, stampX, stampY);
        } else {
            graphics.renderFakeItem(stampIcon, stampX, stampY);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.fill(stampX, stampY, stampX + 16, stampY + 16, 0xA88B8B8B);
            graphics.pose().popPose();
        }

        // Stock grid
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int x = leftPos + GRID_X + column * CELL;
                int y = topPos + GRID_Y + row * CELL;
                GuiDraw.slot(graphics, x, y);

                int index = (scrollRow + row) * COLUMNS + column;
                if (index < visibleEntries.size()) {
                    DepositoryStock.Entry entry = visibleEntries.get(index);
                    if (getOrdered(entry) > 0) {
                        graphics.fill(x, y, x + 16, y + 16, ORDERED_BACKGROUND);
                    }
                    graphics.renderItem(entry.item(), x, y);
                    graphics.renderItemDecorations(font, entry.item(), x, y, "");
                    if (entry.count() > 1) {
                        drawCount(graphics, formatCount(entry.count()), x, y);
                    }
                }
            }
        }

        // Scrollbar
        int trackX = leftPos + SCROLLBAR_X;
        int trackY = topPos + GRID_Y;
        int trackHeight = GRID_ROWS * CELL - 2;
        GuiDraw.inset(graphics, trackX, trackY, SCROLLBAR_WIDTH, trackHeight);
        int maxScroll = maxScrollRow();
        int thumbHeight = 15;
        int thumbY = trackY + (maxScroll == 0 ? 0 : (trackHeight - thumbHeight) * scrollRow / maxScroll);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, maxScroll == 0 ? 0xFFA0A0A0 : 0xFFE0E0E0);
        graphics.fill(trackX + 1, thumbY + 1, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, maxScroll == 0 ? 0xFF8B8B8B : 0xFFB0B0B0);

        // Order
        for (int row = 0; row < ORDER_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int x = leftPos + GRID_X + column * CELL;
                int y = topPos + ORDER_Y + row * CELL;
                GuiDraw.slot(graphics, x, y);

                int index = row * COLUMNS + column;
                if (index < order.size()) {
                    OrderLine line = order.get(index);
                    graphics.renderItem(line.item, x, y);
                    graphics.renderItemDecorations(font, line.item, x, y, "");
                    drawCount(graphics, formatCount(line.amount), x, y);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        @Nullable StockSnapshot snapshot = menu.getSnapshot();

        if (snapshot != null) {
            Component storage = Component.translatable("gui.envelope_automate.depository_terminal.storage",
                  snapshot.storedMail(), snapshot.capacity());
            int storageX = imageWidth - 8 - font.width(storage);
            graphics.drawString(font, storage, storageX, titleLabelY, TEXT_COLOR, false);

            // Header shows whose account this is (a fingerprinted stamp can point it at another player)
            boolean fingerprinted = Fingerprint.get(snapshot.stamp()) != null;
            String header = Component.translatable("gui.envelope_automate.depository_terminal.account", snapshot.account()).getString();
            int maxWidth = storageX - 6 - titleLabelX;
            if (font.width(header) > maxWidth) {
                header = font.plainSubstrByWidth(header, maxWidth - font.width("...")) + "...";
            }
            graphics.drawString(font, header, titleLabelX, titleLabelY, fingerprinted ? FINGERPRINT_COLOR : TEXT_COLOR, false);
        } else {
            graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        }

        Component orderLabel = Component.translatable("gui.envelope_automate.depository_terminal.order", order.size());
        graphics.drawString(font, orderLabel, GRID_X, ORDER_LABEL_Y, TEXT_COLOR, false);
        int hiddenLines = order.size() - ORDER_ROWS * COLUMNS;
        if (hiddenLines > 0) {
            String more = "+" + hiddenLines;
            graphics.drawString(font, more, imageWidth - 8 - font.width(more), ORDER_LABEL_Y, TEXT_COLOR, false);
        }

        graphics.drawString(font, Component.translatable("gui.envelope_automate.depository_terminal.destination"),
              GRID_X, DESTINATION_LABEL_Y, TEXT_COLOR, false);

        // Messages shown over the empty stock grid
        @Nullable Component gridMessage = null;
        int gridMessageColor = 0xFFFFFF;
        if (snapshot == null) {
            gridMessage = Component.translatable("gui.envelope_automate.depository_terminal.loading");
        } else if (!snapshot.serviceEnabled()) {
            gridMessage = Component.translatable("gui.envelope_automate.depository_terminal.disabled");
            gridMessageColor = WARN_COLOR;
        } else if (allEntries.isEmpty()) {
            gridMessage = Component.translatable("gui.envelope_automate.depository_terminal.empty");
        }

        if (gridMessage != null) {
            List<FormattedCharSequence> lines = font.split(gridMessage, COLUMNS * CELL - 8);
            int y = GRID_Y + (GRID_ROWS * CELL - lines.size() * font.lineHeight) / 2;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, GRID_X + (COLUMNS * CELL - font.width(line)) / 2, y, gridMessageColor, true);
                y += font.lineHeight;
            }
        }
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) {
            return;
        }

        if (isOverStamp(mouseX, mouseY)) {
            ItemStack stamp = getInsertedStamp();
            if (stamp.isEmpty()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.envelope_automate.depository_terminal.stamp_slot").getVisualOrderText());
                lines.addAll(font.split(Component.translatable("gui.envelope_automate.depository_terminal.stamp_slot.description")
                      .withStyle(ChatFormatting.GRAY), 200));
                graphics.renderTooltip(font, lines, mouseX, mouseY);
            } else {
                List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stamp));
                lines.add(Component.translatable("gui.envelope_automate.depository_terminal.stamp_slot.take")
                      .withStyle(ChatFormatting.DARK_GRAY));
                graphics.renderTooltip(font, lines, stamp.getTooltipImage(), mouseX, mouseY);
            }
            return;
        }

        @Nullable DepositoryStock.Entry entry = getGridEntryAt(mouseX, mouseY);
        if (entry != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(entry.item()));
            lines.add(Component.translatable("gui.envelope_automate.depository_terminal.stored", entry.count())
                  .withStyle(ChatFormatting.GOLD));
            int ordered = getOrdered(entry);
            if (ordered > 0) {
                lines.add(Component.translatable("gui.envelope_automate.depository_terminal.ordered", ordered)
                      .withStyle(ChatFormatting.GREEN));
            }
            if (entry.whole()) {
                lines.add(Component.translatable("gui.envelope_automate.depository_terminal.whole_mail")
                      .withStyle(ChatFormatting.DARK_AQUA));
            }
            lines.add(Component.translatable("gui.envelope_automate.depository_terminal.controls")
                  .withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderTooltip(font, lines, entry.item().getTooltipImage(), mouseX, mouseY);
            highlightCell(graphics, mouseX, mouseY, GRID_Y);
            return;
        }

        @Nullable OrderLine line = getOrderLineAt(mouseX, mouseY);
        if (line != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(line.item));
            lines.add(Component.translatable("gui.envelope_automate.depository_terminal.ordered", line.amount)
                  .withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("gui.envelope_automate.depository_terminal.order_controls")
                  .withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderTooltip(font, lines, line.item.getTooltipImage(), mouseX, mouseY);
        }
    }

    private void highlightCell(GuiGraphics graphics, int mouseX, int mouseY, int areaY) {
        int column = (mouseX - leftPos - GRID_X) / CELL;
        int row = (mouseY - topPos - areaY) / CELL;
        int x = leftPos + GRID_X + column * CELL;
        int y = topPos + areaY + row * CELL;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        graphics.fillGradient(x, y, x + 16, y + 16, 0x80FFFFFF, 0x80FFFFFF);
        graphics.pose().popPose();
    }

    /**
     * Bottom-right aligned like vanilla stack counts, but at half size when the text is wider than the slot.
     */
    private void drawCount(GuiGraphics graphics, String text, int x, int y) {
        int width = font.width(text);
        float scale = width > 16 ? 0.5f : 1f;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 17, y + 17, 200);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, -width, -8, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private static String formatCount(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 10_000) return String.format(Locale.ROOT, "%.1fk", count / 1000f);
        if (count < 1_000_000) return (count / 1000) + "k";
        return String.format(Locale.ROOT, "%.1fM", count / 1_000_000f);
    }

    // -- Input

    private int cellIndexAt(double mouseX, double mouseY, int areaY, int rows) {
        double localX = mouseX - leftPos - GRID_X;
        double localY = mouseY - topPos - areaY;
        if (localX < 0 || localY < 0 || localX >= COLUMNS * CELL || localY >= rows * CELL) {
            return -1;
        }
        return (int) (localY / CELL) * COLUMNS + (int) (localX / CELL);
    }

    private @Nullable DepositoryStock.Entry getGridEntryAt(double mouseX, double mouseY) {
        int cell = cellIndexAt(mouseX, mouseY, GRID_Y, GRID_ROWS);
        if (cell < 0) return null;
        int index = scrollRow * COLUMNS + cell;
        return index < visibleEntries.size() ? visibleEntries.get(index) : null;
    }

    private @Nullable OrderLine getOrderLineAt(double mouseX, double mouseY) {
        int index = cellIndexAt(mouseX, mouseY, ORDER_Y, ORDER_ROWS);
        return index >= 0 && index < order.size() ? order.get(index) : null;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLLBAR_X && mouseX < leftPos + SCROLLBAR_X + SCROLLBAR_WIDTH
              && mouseY >= topPos + GRID_Y && mouseY < topPos + GRID_Y + GRID_ROWS * CELL;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOverStamp(mouseX, mouseY)) {
            if (!getInsertedStamp().isEmpty()) {
                PacketDistributor.sendToServer(ServerboundTakeStampPayload.INSTANCE);
            }
            return true;
        }

        @Nullable DepositoryStock.Entry entry = getGridEntryAt(mouseX, mouseY);
        if (entry != null && (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT)) {
            int step = hasShiftDown() ? Math.max(1, entry.item().getMaxStackSize()) : 1;
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                changeOrder(entry, step, hasControlDown());
            } else {
                changeOrder(entry, -step, false);
            }
            return true;
        }

        @Nullable OrderLine line = getOrderLineAt(mouseX, mouseY);
        if (line != null) {
            int step = hasShiftDown() ? line.amount : (button == InputConstants.MOUSE_BUTTON_LEFT ? 1 : Math.max(1, line.item.getMaxStackSize()));
            line.amount -= step;
            if (line.amount <= 0) {
                order.remove(line);
            }
            return true;
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT && isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollToMouse(double mouseY) {
        int maxScroll = maxScrollRow();
        if (maxScroll == 0) return;
        double progress = (mouseY - topPos - GRID_Y - 7.5) / (GRID_ROWS * CELL - 2 - 15);
        scrollRow = Mth.clamp((int) Math.round(progress * maxScroll), 0, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (destinationBox.isFocused()) {
            if (keyCode == InputConstants.KEY_TAB) {
                Optional<String> completion = getSuggestedCompletion();
                if (completion.isPresent()) {
                    destinationBox.setValue(completion.get());
                    return true;
                }
            }
            if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) && sendButton.active) {
                sendOrder();
                return true;
            }
            destinationBox.keyPressed(keyCode, scanCode, modifiers);
            updateDestinationSuggestion();
            return true;
        }

        if (searchBox.isFocused()) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static class OrderLine {
        private final ItemStack item;
        private int amount;
        private final boolean whole;

        private OrderLine(ItemStack item, int amount, boolean whole) {
            this.item = item.copyWithCount(1);
            this.amount = amount;
            this.whole = whole;
        }
    }
}

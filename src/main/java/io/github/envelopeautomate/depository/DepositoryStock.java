package io.github.envelopeautomate.depository;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloud Depository accounts store whole mail items. Packages are "opened up" so their contents can be
 * browsed and withdrawn item by item; anything else (letters, packages with unrolled loot, etc.) is
 * listed and withdrawn as a whole mail item.
 */
public final class DepositoryStock {
    private DepositoryStock() {
    }

    public record Entry(ItemStack item, int count, boolean whole) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
              ItemStack.STREAM_CODEC, Entry::item,
              ByteBufCodecs.VAR_INT, Entry::count,
              ByteBufCodecs.BOOL, Entry::whole,
              Entry::new
        );
    }

    public record Withdrawal(List<ItemStack> looseItems, List<ItemStack> wholeMail, int experience) {
        public boolean isEmpty() {
            return looseItems.isEmpty() && wholeMail.isEmpty();
        }
    }

    public static boolean isBrowsablePackage(ItemStack stored) {
        return (stored.is(Envelope.Items.PACKAGE.get()) || stored.is(Envelope.Items.SEALED_PACKAGE.get()))
              && !stored.has(DataComponents.CONTAINER_LOOT)
              && !PackageContents.of(stored).isEmpty();
    }

    public static List<Entry> collect(List<ItemStack> storedMail) {
        List<ItemStack> templates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<Boolean> wholes = new ArrayList<>();

        for (ItemStack stored : storedMail) {
            if (stored.isEmpty()) continue;

            if (isBrowsablePackage(stored)) {
                for (ItemStack content : PackageContents.of(stored).getItems()) {
                    if (!content.isEmpty()) {
                        add(templates, counts, wholes, content, content.getCount(), false);
                    }
                }
            } else {
                add(templates, counts, wholes, stored, stored.getCount(), true);
            }
        }

        List<Entry> entries = new ArrayList<>(templates.size());
        for (int i = 0; i < templates.size(); i++) {
            entries.add(new Entry(templates.get(i), counts.get(i), wholes.get(i)));
        }
        return entries;
    }

    private static void add(List<ItemStack> templates, List<Integer> counts, List<Boolean> wholes,
                            ItemStack stack, int count, boolean whole) {
        for (int i = 0; i < templates.size(); i++) {
            if (wholes.get(i) == whole && ItemStack.isSameItemSameComponents(templates.get(i), stack)) {
                counts.set(i, counts.get(i) + count);
                return;
            }
        }
        templates.add(stack.copyWithCount(1));
        counts.add(count);
        wholes.add(whole);
    }

    /**
     * Removes requested items from the stored mail list (newest mail first). Package contents are reduced in place;
     * packages that become empty are removed from storage.
     */
    public static Withdrawal withdraw(List<ItemStack> storedMail, List<WithdrawalOrder.Line> lines) {
        List<ItemStack> loose = new ArrayList<>();
        List<ItemStack> whole = new ArrayList<>();
        int experience = 0;

        for (WithdrawalOrder.Line line : lines) {
            int remaining = line.amount();

            for (int index = storedMail.size() - 1; index >= 0 && remaining > 0; index--) {
                ItemStack stored = storedMail.get(index);

                if (line.whole()) {
                    if (!isBrowsablePackage(stored) && line.matches(stored, true)) {
                        storedMail.remove(index);
                        whole.add(stored);
                        remaining -= stored.getCount();
                    }
                    continue;
                }

                if (!isBrowsablePackage(stored)) {
                    continue;
                }

                List<ItemStack> contents = new ArrayList<>();
                for (ItemStack content : PackageContents.of(stored).getItems()) {
                    contents.add(content.copy());
                }

                int taken = 0;
                for (ItemStack content : contents) {
                    if (remaining - taken <= 0) break;
                    if (!content.isEmpty() && line.matches(content, false)) {
                        int take = Math.min(remaining - taken, content.getCount());
                        content.shrink(take);
                        taken += take;
                    }
                }

                if (taken == 0) {
                    continue;
                }

                remaining -= taken;
                addSplit(loose, line.item(), taken);

                if (contents.stream().allMatch(ItemStack::isEmpty)) {
                    storedMail.remove(index);
                    experience += stored.getOrDefault(Envelope.DataComponents.PACKAGE_EXPERIENCE, 0);
                } else {
                    stored.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(contents));
                }
            }
        }

        return new Withdrawal(loose, whole, experience);
    }

    private static void addSplit(List<ItemStack> out, ItemStack template, int amount) {
        int maxStackSize = Math.max(1, template.getMaxStackSize());
        while (amount > 0) {
            int count = Math.min(amount, maxStackSize);
            out.add(template.copyWithCount(count));
            amount -= count;
        }
    }
}

package dev.mtop.hungryloop.menu;

import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.curio.LoopSlotMirror;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Menu over one Hungry Loop's sub-slots. The loop is found by {@link LoopLocator}; its
 * {@link LoopContents} component is loaded into a scratch {@link SimpleContainer} on open and written
 * back on every change and on close.
 */
public class HungryLoopMenu extends AbstractContainerMenu {

    private static final int LOOP_SLOTS_PER_ROW = 6;

    private final Player player;
    private final LoopLocator locator;
    private final SimpleContainer contents;
    private final int loopSlotCount;

    /** Client-side factory: the locator comes over the wire. */
    public HungryLoopMenu(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(windowId, playerInv, LoopLocator.decode(extraData));
    }

    public HungryLoopMenu(int windowId, Inventory playerInv, LoopLocator locator) {
        super(ModMenus.HUNGRY_LOOP.get(), windowId);
        this.player = playerInv.player;
        this.locator = locator;

        ItemStack loop = locator.resolve(player);
        LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
        this.loopSlotCount = data.totalSlots();
        this.contents = new SimpleContainer(loopSlotCount);
        var slotList = data.toSlotList();
        for (int i = 0; i < loopSlotCount; i++) {
            this.contents.setItem(i, slotList.get(i));
        }
        this.contents.addListener(c -> persist());

        addLoopSlots();
        addPlayerSlots(playerInv);
    }

    private void addLoopSlots() {
        int rows = Math.max(1, (loopSlotCount + LOOP_SLOTS_PER_ROW - 1) / LOOP_SLOTS_PER_ROW);
        for (int i = 0; i < loopSlotCount; i++) {
            int row = i / LOOP_SLOTS_PER_ROW;
            int col = i % LOOP_SLOTS_PER_ROW;
            int rowCount = Math.min(LOOP_SLOTS_PER_ROW, loopSlotCount - row * LOOP_SLOTS_PER_ROW);
            int rowWidth = rowCount * 18;
            int x = (176 - rowWidth) / 2 + col * 18 + 1;
            int y = 18 + row * 18 + (rows == 1 ? 9 : 0);
            addSlot(new Slot(contents, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return isEquippableCurio(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
    }

    private void addPlayerSlots(Inventory inv) {
        int invY = 18 + 3 * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, invY + 58));
        }
    }

    /** A curio we're willing to hold - anything with a Curios capability, except another loop. */
    private static boolean isEquippableCurio(ItemStack stack) {
        return !stack.isEmpty()
                && !(stack.getItem() instanceof HungryLoopItem)
                && CuriosApi.getCurio(stack).isPresent();
    }

    private void persist() {
        if (player.level().isClientSide) return;

        ItemStack loop = locator.resolve(player);
        if (!(loop.getItem() instanceof HungryLoopItem)) return;

        LoopContents current = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
        java.util.List<ItemStack> items = new java.util.ArrayList<>(loopSlotCount);
        for (int i = 0; i < loopSlotCount; i++) {
            items.add(contents.getItem(i));
        }
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), current.withItems(items));
        locator.afterChange(player, loop);
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        persist();
        LoopSlotMirror.syncIfServer(who); // push contents changes into the granted curio slots now
    }

    @Override
    public boolean stillValid(Player who) {
        return locator.resolve(who).getItem() instanceof HungryLoopItem;
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean fromLoop = index < loopSlotCount;

        if (fromLoop) {
            if (!moveItemStackTo(stack, loopSlotCount, slots.size(), true)) return ItemStack.EMPTY;
        } else if (isEquippableCurio(stack)) {
            if (!moveItemStackTo(stack, 0, loopSlotCount, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    public int loopSlotCount() {
        return loopSlotCount;
    }

    public Container contentsContainer() {
        return contents;
    }
}

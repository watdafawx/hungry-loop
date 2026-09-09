package dev.mtop.hungryloop.menu;

import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.curio.LoopSlotMirror;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
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

    public static final int LOOP_SLOTS_PER_ROW = 6;
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 192;
    public static final int LOOP_ORIGIN_Y = 20;
    public static final int PLAYER_INV_Y = 108;

    public static final int BUTTON_FEED_ONE = 0;
    public static final int BUTTON_FEED_SLOT = 1;

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

    public static int loopSlotX(int index, int slotCount) {
        int row = index / LOOP_SLOTS_PER_ROW;
        int col = index % LOOP_SLOTS_PER_ROW;
        int rowCount = Math.min(LOOP_SLOTS_PER_ROW, slotCount - row * LOOP_SLOTS_PER_ROW);
        return (PANEL_WIDTH - rowCount * 18) / 2 + col * 18 + 1;
    }

    public static int loopSlotY(int index) {
        return LOOP_ORIGIN_Y + (index / LOOP_SLOTS_PER_ROW) * 18;
    }

    private void addLoopSlots() {
        for (int i = 0; i < loopSlotCount; i++) {
            addSlot(new Slot(contents, i, loopSlotX(i, loopSlotCount), loopSlotY(i)) {
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
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, PLAYER_INV_Y + 58));
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

    /** Button 0: feed one XP level. Button 1: feed as many levels as the next slot needs. */
    @Override
    public boolean clickMenuButton(Player who, int buttonId) {
        if (who.level().isClientSide) return true;

        ItemStack loop = locator.resolve(who);
        if (!(loop.getItem() instanceof HungryLoopItem)) return false;

        LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
        if (!data.canGrow() || who.experienceLevel <= 0) return false;

        int need = data.nextSlotCost() - data.storedXp();
        int feed = Math.min(who.experienceLevel, buttonId == 1 ? Math.max(1, need) : 1);
        if (feed <= 0) return false;

        who.giveExperienceLevels(-feed);
        LoopContents fed = data.feed(feed);
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), fed);

        if (fed.totalSlots() > data.totalSlots()) {
            who.level().playSound(null, who.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
            if (who instanceof ServerPlayer sp) {
                open(sp, locator); // reopen so the new slots appear
            }
        } else {
            broadcastChanges();
        }
        return true;
    }

    /** Live view of the loop for the screen (client resolves its own synced copy). */
    public LoopContents loopData() {
        return locator.resolve(player).getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
    }

    public int playerXpLevels() {
        return player.experienceLevel;
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

    /** Opens the loop menu for {@code player}, resolving the loop via {@code locator} each time. */
    public static void open(ServerPlayer player, LoopLocator locator) {
        if (!(locator.resolve(player).getItem() instanceof HungryLoopItem)) return;

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new HungryLoopMenu(id, inv, locator),
                locator.resolve(player).getHoverName());
        player.openMenu(provider, locator::encode);
    }

    public int loopSlotCount() {
        return loopSlotCount;
    }

    public Container contentsContainer() {
        return contents;
    }
}

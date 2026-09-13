package dev.mtop.hungryloop.menu;

import dev.mtop.hungryloop.Config;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.curio.LoopSlotMirror;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu over one Hungry Loop's sub-slots. The loop is found by {@link LoopLocator}; its
 * {@link LoopContents} component is the source of truth, loaded into a scratch {@link SimpleContainer}.
 *
 * <p>The menu always carries {@link #MAX_LOOP_SLOTS} sub-slots; the ones past the loop's current
 * total are {@linkplain Slot#isActive() inactive} and drawn as "locked" nodes by the screen. Growing
 * the loop just flips slots to active - the menu never has to be rebuilt, so opening the screen and
 * feeding it never moves the cursor.
 */
public class HungryLoopMenu extends AbstractContainerMenu {

    /** Hard ceiling on sub-slots; matches the highest {@code absoluteMaxSlots} can be. */
    public static final int MAX_LOOP_SLOTS = 54;

    public static final int PANEL_WIDTH = 218;
    public static final int PANEL_HEIGHT = 246;
    public static final int PLAYER_INV_Y = 162;

    public static final int BUTTON_FEED_ONE = 0;
    public static final int BUTTON_FEED_ALL = 1;
    public static final int BUTTON_STAR = 2;
    public static final int BUTTON_RENDER_SELF = 3;
    /** Click the locked slot at {@code id - BUTTON_SLOT_BASE} to unlock up to it. */
    public static final int BUTTON_SLOT_BASE = 100;
    /** Toggle the cosmetic render of contained item {@code id - BUTTON_RENDER_BASE}. */
    public static final int BUTTON_RENDER_BASE = 200;

    private static final int SNAKE_COLS = 6;
    private static final int SNAKE_X0 = 18;
    private static final int SNAKE_DX = 30;
    private static final int SNAKE_Y0 = 26;
    private static final int SNAKE_DY = 24;

    private final Player player;
    private final LoopLocator locator;
    private final SimpleContainer contents;

    public HungryLoopMenu(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(windowId, playerInv, LoopLocator.decode(extraData));
    }

    HungryLoopMenu(int windowId, Inventory playerInv, LoopLocator locator) {
        super(ModMenus.HUNGRY_LOOP.get(), windowId);
        this.player = playerInv.player;
        this.locator = locator;
        this.contents = new SimpleContainer(MAX_LOOP_SLOTS);

        var slotList = loopData().toSlotList();
        for (int i = 0; i < Math.min(MAX_LOOP_SLOTS, slotList.size()); i++) {
            contents.setItem(i, slotList.get(i));
        }
        contents.addListener(c -> persist());

        for (int i = 0; i < MAX_LOOP_SLOTS; i++) {
            addSlot(new LoopSlot(i));
        }
        addPlayerSlots(playerInv);
    }

    private final class LoopSlot extends Slot {
        LoopSlot(int index) {
            super(contents, index, loopSlotX(index), loopSlotY(index));
        }

        /**
         * Unlocked slots, plus any slot holding an overflow item - contents past {@link
         * #unlockedSlots()} are still equipped through Curios, so they have to be visible and
         * removable rather than silently stranded. {@link #mayPlace} keeps them take-only.
         */
        @Override
        public boolean isActive() {
            return getContainerSlot() < unlockedSlots() || hasItem();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return getContainerSlot() < unlockedSlots() && isEquippableCurio(stack);
        }

        @Override
        public boolean mayPickup(Player who) {
            return isActive();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // --- snake layout ---------------------------------------------------------------------------

    public static int loopSlotX(int index) {
        int row = index / SNAKE_COLS;
        int col = index % SNAKE_COLS;
        int eff = row % 2 == 0 ? col : SNAKE_COLS - 1 - col; // serpentine
        return SNAKE_X0 + eff * SNAKE_DX + (int) Math.round(Math.cos(index * 0.5) * 2);
    }

    public static int loopSlotY(int index) {
        int row = index / SNAKE_COLS;
        return SNAKE_Y0 + row * SNAKE_DY + (int) Math.round(Math.sin(index * 0.8) * 3);
    }

    public static int[] loopSlotCenter(int index) {
        return new int[]{loopSlotX(index) + 8, loopSlotY(index) + 8};
    }

    private void addPlayerSlots(Inventory inv) {
        int x0 = (PANEL_WIDTH - 9 * 18) / 2;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, x0 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, x0 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    private static boolean isEquippableCurio(ItemStack stack) {
        return !stack.isEmpty()
                && !(stack.getItem() instanceof HungryLoopItem)
                && CuriosApi.getCurio(stack).isPresent();
    }

    // --- state ---------------------------------------------------------------------------------

    public LoopContents loopData() {
        return locator.resolve(player).getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
    }

    /** How many sub-slots are unlocked right now (the rest are shown locked). */
    public int unlockedSlots() {
        return Math.min(loopData().totalSlots(), MAX_LOOP_SLOTS);
    }

    public int playerXpLevels() {
        return player.experienceLevel;
    }

    private void persist() {
        if (player.level().isClientSide) return;

        ItemStack loop = locator.resolve(player);
        if (!(loop.getItem() instanceof HungryLoopItem)) return;

        LoopContents current = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
        List<ItemStack> items = new ArrayList<>(MAX_LOOP_SLOTS);
        for (int i = 0; i < MAX_LOOP_SLOTS; i++) {
            items.add(contents.getItem(i));
        }
        LoopContents next = current.withItems(items);
        if (next.equals(current)) return; // the container listener fires several times per edit
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), next);
        locator.afterChange(player, loop);
        LoopSlotMirror.syncIfServer(player);
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        persist();
        LoopSlotMirror.syncIfServer(who);
    }

    @Override
    public boolean stillValid(Player who) {
        return locator.resolve(who).getItem() instanceof HungryLoopItem;
    }

    // --- buttons ------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player who, int buttonId) {
        if (who.level().isClientSide) return true;

        ItemStack loop = locator.resolve(who);
        if (!(loop.getItem() instanceof HungryLoopItem)) return false;

        LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);

        if (buttonId == BUTTON_RENDER_SELF || buttonId >= BUTTON_RENDER_BASE) {
            LoopContents toggled = buttonId == BUTTON_RENDER_SELF
                    ? data.toggleSelfRender()
                    : data.toggleItemRender(buttonId - BUTTON_RENDER_BASE);
            loop.set(ModDataComponents.LOOP_CONTENTS.get(), toggled);
            locator.afterChange(who, loop);
            if (who instanceof ServerPlayer sp) {
                LoopSlotMirror.sync(sp, true);
            }
            broadcastChanges();
            return true;
        }

        if (buttonId == BUTTON_STAR) {
            ItemStack carried = getCarried();
            if (!carried.is(Items.NETHER_STAR) || !data.canRaiseCap()) return false;
            carried.shrink(1);
            loop.set(ModDataComponents.LOOP_CONTENTS.get(),
                    data.withCapBonus(data.capBonus() + Config.slotsPerStar()));
            who.level().playSound(null, who.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 0.5f, 1.6f);
            broadcastChanges();
            return true;
        }

        if (!data.canGrow() || who.experienceLevel <= 0) return false;

        int feed;
        if (buttonId >= BUTTON_SLOT_BASE) {
            feed = Math.min(who.experienceLevel, data.costToReach(buttonId - BUTTON_SLOT_BASE + 1));
        } else {
            feed = buttonId == BUTTON_FEED_ALL ? who.experienceLevel : 1;
        }
        if (feed <= 0) return false;

        who.giveExperienceLevels(-feed);
        LoopContents fed = data.feed(feed);
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), fed);

        if (fed.totalSlots() > data.totalSlots()) {
            who.level().playSound(null, who.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        broadcastChanges(); // slots flip active on their own - no rebuild
        return true;
    }

    // --- shift-click --------------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.isActive()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean fromLoop = index < MAX_LOOP_SLOTS;

        if (fromLoop) {
            if (!moveItemStackTo(stack, MAX_LOOP_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (isEquippableCurio(stack)) {
            if (!moveItemStackTo(stack, 0, unlockedSlots(), false)) return ItemStack.EMPTY;
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

    // --- open ---------------------------------------------------------------------------------

    public static void open(ServerPlayer player, LoopLocator locator) {
        ItemStack loop = locator.resolve(player);
        if (!(loop.getItem() instanceof HungryLoopItem)) return;

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new HungryLoopMenu(id, inv, locator), loop.getHoverName());
        player.openMenu(provider, locator::encode);
    }

    public Container contentsContainer() {
        return contents;
    }
}

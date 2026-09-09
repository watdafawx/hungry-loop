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

    public static final int PANEL_WIDTH = 218;
    public static final int PANEL_HEIGHT = 246;
    public static final int PLAYER_INV_Y = 162;

    public static final int BUTTON_FEED_ONE = 0;
    public static final int BUTTON_FEED_ALL = 1;

    // Snake-constellation layout: a serpentine path with a gentle undulation.
    private static final int SNAKE_COLS = 6;
    private static final int SNAKE_X0 = 18;
    private static final int SNAKE_DX = 30;
    private static final int SNAKE_Y0 = 26;
    private static final int SNAKE_DY = 24;

    private final Player player;
    private final LoopLocator locator;
    private final SimpleContainer contents;
    private final int loopSlotCount;

    /** Client-side factory: locator + slot count come over the wire, so the two sides always agree. */
    public HungryLoopMenu(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(windowId, playerInv, LoopLocator.decode(extraData), extraData.readVarInt());
    }

    HungryLoopMenu(int windowId, Inventory playerInv, LoopLocator locator, int slotCount) {
        super(ModMenus.HUNGRY_LOOP.get(), windowId);
        this.player = playerInv.player;
        this.locator = locator;
        this.loopSlotCount = slotCount;

        LoopContents data = locator.resolve(player)
                .getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
        this.contents = new SimpleContainer(loopSlotCount);
        var slotList = data.toSlotList();
        for (int i = 0; i < Math.min(loopSlotCount, slotList.size()); i++) {
            this.contents.setItem(i, slotList.get(i));
        }
        this.contents.addListener(c -> persist());

        addLoopSlots();
        addPlayerSlots(playerInv);
    }

    /** Top-left corner of sub-slot {@code index} along the snake path. */
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

    /** Centre point of sub-slot {@code index} (for drawing the constellation lines through it). */
    public static int[] loopSlotCenter(int index) {
        return new int[]{loopSlotX(index) + 8, loopSlotY(index) + 8};
    }

    private void addLoopSlots() {
        for (int i = 0; i < loopSlotCount; i++) {
            addSlot(new Slot(contents, i, loopSlotX(i), loopSlotY(i)) {
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

    public static final int BUTTON_STAR = 2;
    /** Buttons {@code >= BUTTON_SLOT_BASE}: click the locked slot at (id - base) to unlock up to it. */
    public static final int BUTTON_SLOT_BASE = 100;

    /**
     * Button 0: feed one XP level. Button 1: feed every XP level you have. Button 2: spend one
     * Nether Star from the cursor to raise the cap. Button {@code 100 + k}: feed enough XP to reach
     * {@code k + 1} total slots (click a locked slot).
     */
    @Override
    public boolean clickMenuButton(Player who, int buttonId) {
        if (who.level().isClientSide) return true;

        ItemStack loop = locator.resolve(who);
        if (!(loop.getItem() instanceof HungryLoopItem)) return false;

        LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);

        if (buttonId == BUTTON_STAR) {
            ItemStack carried = getCarried();
            if (!carried.is(net.minecraft.world.item.Items.NETHER_STAR) || !data.canRaiseCap()) return false;
            carried.shrink(1);
            loop.set(ModDataComponents.LOOP_CONTENTS.get(),
                    data.withCapBonus(data.capBonus() + dev.mtop.hungryloop.Config.slotsPerStar()));
            who.level().playSound(null, who.blockPosition(), net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.6f);
            broadcastChanges();
            return true;
        }

        if (!data.canGrow() || who.experienceLevel <= 0) return false;

        int feed;
        if (buttonId >= BUTTON_SLOT_BASE) {
            int target = buttonId - BUTTON_SLOT_BASE + 1; // total slot count to reach
            feed = Math.min(who.experienceLevel, data.costToReach(target));
        } else {
            feed = buttonId == 1 ? who.experienceLevel : 1;
        }
        if (feed <= 0) return false;

        who.giveExperienceLevels(-feed);
        LoopContents fed = data.feed(feed);
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), fed);

        if (fed.totalSlots() > data.totalSlots() && who instanceof ServerPlayer sp) {
            who.level().playSound(null, who.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
            sp.server.execute(() -> open(sp, locator)); // reopen after the tick for the new slots
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

    /** Opens the loop menu for {@code player}, resolving the loop via {@code locator}. */
    public static void open(ServerPlayer player, LoopLocator locator) {
        ItemStack loop = locator.resolve(player);
        if (!(loop.getItem() instanceof HungryLoopItem)) return;

        int slots = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT).totalSlots();
        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new HungryLoopMenu(id, inv, locator, slots),
                loop.getHoverName());
        player.openMenu(provider, buf -> {
            locator.encode(buf);
            buf.writeVarInt(slots);
        });
    }

    public int loopSlotCount() {
        return loopSlotCount;
    }

    public Container contentsContainer() {
        return contents;
    }
}

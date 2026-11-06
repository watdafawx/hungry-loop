package dev.watdafawx.hungryloop.client;

import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.integration.accessories.AccessoriesClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

/**
 * Makes the loop screen an overlay over the inventory-type screen it was opened from: that screen
 * keeps drawing (dimmed) behind it, and closing the loop goes back to it instead of to the game.
 *
 * <p>Server menus don't stack - opening the loop's menu closes the parent's - so "going back" means
 * reopening the parent: the player inventory and creative screens are client-only, Curios and
 * Accessories are asked to reopen theirs. Any other screen (a chest, ...) isn't remembered, and the
 * loop behaves as a plain screen.
 */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class LoopOverlay {

    private static final boolean ACCESSORIES = ModList.get().isLoaded("accessories");

    private static Screen parent;

    /**
     * A returnable screen that just closed. Curios' and Accessories' screens are server menus: the
     * server closes them before opening the loop's, so the client sees "parent -> nothing -> loop".
     * If the loop screen opens within {@link #ADOPT_WINDOW} ticks, it adopts this one.
     */
    private static Screen lastClosed;
    private static long lastClosedAt;
    private static final long ADOPT_WINDOW = 20;

    private LoopOverlay() {}

    @SubscribeEvent
    static void onOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof HungryLoopScreen) {
            Screen current = event.getCurrentScreen();
            if (isReturnable(current)) {
                parent = current;
            } else if (current == null && lastClosed != null && now() - lastClosedAt <= ADOPT_WINDOW) {
                parent = lastClosed;
            } else {
                parent = null;
            }
            lastClosed = null;
        } else if (event.getNewScreen() != null) {
            parent = null;
        }
    }

    @SubscribeEvent
    static void onClosing(ScreenEvent.Closing event) {
        if (isReturnable(event.getScreen())) {
            lastClosed = event.getScreen();
            lastClosedAt = now();
        }
    }

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    private static boolean isReturnable(Screen screen) {
        return screen instanceof InventoryScreen
                || screen instanceof CreativeModeInventoryScreen
                || screen instanceof CuriosScreen
                || (ACCESSORIES && AccessoriesClientHooks.isAccessoriesScreen(screen));
    }

    static boolean hasParent() {
        return parent != null;
    }

    /**
     * Draws the parent as the backdrop. The parent draws its own background (and the one-per-frame
     * blur), so the loop screen must not draw another - it only dims on top.
     */
    static void renderBackdrop(GuiGraphics g, float partialTick) {
        try {
            parent.render(g, -1, -1, partialTick);
        } catch (RuntimeException e) {
            HungryLoop.LOG.debug("loop overlay: parent screen failed to render, dropping it", e);
            parent = null;
            return;
        }
        // The parent's items sit at raised z; start the loop screen with a clean depth buffer so the
        // dim and everything after it draw on top, whatever depth the parent left behind.
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xA0000000);
    }

    static void resize(Minecraft mc, int width, int height) {
        if (parent != null) parent.resize(mc, width, height);
    }

    /** After the loop screen closed itself: go back to where it was opened from. */
    static void returnToParent() {
        Screen back = parent;
        parent = null;
        Minecraft mc = Minecraft.getInstance();
        if (back == null || mc.player == null) return;

        if (back instanceof CreativeModeInventoryScreen) {
            mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                    mc.options.operatorItemsTab().get()));
        } else if (back instanceof InventoryScreen) {
            mc.setScreen(new InventoryScreen(mc.player));
        } else if (back instanceof CuriosScreen) {
            PacketDistributor.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY));
        } else if (ACCESSORIES && AccessoriesClientHooks.isAccessoriesScreen(back)) {
            AccessoriesClientHooks.reopen(back);
        }
    }
}

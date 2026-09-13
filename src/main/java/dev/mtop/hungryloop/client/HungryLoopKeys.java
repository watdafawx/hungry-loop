package dev.mtop.hungryloop.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.item.HungryLoopItem;
import dev.mtop.hungryloop.net.OpenLoopPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind to open the loop from anywhere ({@code net.OpenLoopPayload}), and scrolling over a
 * loop's slot in a screen pages its contents tooltip.
 */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class HungryLoopKeys {

    public static final String CATEGORY = "key.categories.hungryloop";

    public static final KeyMapping OPEN = new KeyMapping(
            "key.hungryloop.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private HungryLoopKeys() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            PacketDistributor.sendToServer(new OpenLoopPayload());
        }
    }

    /** In an inventory screen, scrolling while a loop tooltip is showing pages it. */
    @SubscribeEvent
    static void onScreenScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScrollDeltaY() == 0) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> acs)) return;

        Slot slot = acs.getSlotUnderMouse();
        if (slot == null || !(slot.getItem().getItem() instanceof HungryLoopItem)) return;

        TooltipPager.scroll(event.getScrollDeltaY() > 0 ? -1 : 1);
        event.setCanceled(true);
    }
}

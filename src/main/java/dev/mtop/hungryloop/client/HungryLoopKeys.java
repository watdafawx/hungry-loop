package dev.mtop.hungryloop.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.item.HungryLoopItem;
import dev.mtop.hungryloop.net.CycleLoopPayload;
import dev.mtop.hungryloop.net.OpenLoopPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind to open the loop from anywhere, and shift-scroll on a held loop to cycle its passthrough
 * selection. Both go through the server ({@code net.ModNetwork}).
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

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isShiftKeyDown()) return;
        if (!(player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof HungryLoopItem)) return;

        double dy = event.getScrollDeltaY();
        if (dy == 0) return;

        event.setCanceled(true);
        PacketDistributor.sendToServer(new CycleLoopPayload(dy > 0 ? 1 : -1));
    }
}

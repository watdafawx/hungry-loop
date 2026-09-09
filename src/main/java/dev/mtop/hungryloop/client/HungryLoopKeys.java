package dev.mtop.hungryloop.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.item.HungryLoopItem;
import dev.mtop.hungryloop.net.CycleLoopPayload;
import dev.mtop.hungryloop.net.OpenLoopPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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

    /** In an inventory screen, scrolling while a loop tooltip is showing pages it. */
    @SubscribeEvent
    static void onScreenScroll(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScrollDeltaY() == 0) return;
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs)) return;

        var slot = acs.getSlotUnderMouse();
        if (slot == null || !(slot.getItem().getItem() instanceof HungryLoopItem)) return;

        TooltipPager.scroll(event.getScrollDeltaY() > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isShiftKeyDown()) return;

        double dy = event.getScrollDeltaY();
        if (dy == 0) return;

        ItemStack loop = heldOrEquippedLoop(player);
        if (loop.isEmpty()) return;

        event.setCanceled(true);
        int direction = dy > 0 ? 1 : -1;
        PacketDistributor.sendToServer(new CycleLoopPayload(direction));

        // predict the new selection for the hotbar overlay message (server applies it authoritatively)
        LoopContents next = HungryLoopItem.contentsOf(loop).cycleSelected(direction);
        Component message = next.loopMode()
                ? Component.translatable("hungryloop.overlay.loop")
                : Component.translatable("hungryloop.overlay.selected", next.selectedStack().getHoverName());
        Minecraft.getInstance().gui.setOverlayMessage(message, false);
    }

    /** The loop in the player's main hand, else the first one equipped in a curio slot. */
    private static ItemStack heldOrEquippedLoop(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof HungryLoopItem) {
                return player.getItemInHand(hand);
            }
        }
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.findFirstCurio(s -> s.getItem() instanceof HungryLoopItem))
                .map(r -> r.stack())
                .orElse(ItemStack.EMPTY);
    }
}

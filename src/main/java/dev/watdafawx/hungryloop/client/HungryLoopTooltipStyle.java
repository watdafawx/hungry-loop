package dev.watdafawx.hungryloop.client;

import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** A slowly shifting copper (or aqua, when shiny) border on the loop's tooltip. */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class HungryLoopTooltipStyle {

    private HungryLoopTooltipStyle() {}

    @SubscribeEvent
    static void onColor(RenderTooltipEvent.Color event) {
        if (!(event.getItemStack().getItem() instanceof HungryLoopItem loop)) return;

        LoopFrame.Palette p = LoopFrame.Palette.of(HungryLoopItem.contentsOf(event.getItemStack()).shiny());
        float t = (Util.getMillis() % 3000L) / 3000f * Mth.TWO_PI;

        // Same frame as the loop screen: its body colours, and a border that breathes between the
        // frame's copper and its ember glow (top and bottom out of phase, so it seems to travel).
        event.setBorderStart(breathe(p, t));
        event.setBorderEnd(breathe(p, t + Mth.PI));
        event.setBackgroundStart(LoopFrame.withAlpha(p.bodyTop(), 0xF0));
        event.setBackgroundEnd(LoopFrame.withAlpha(p.bodyBottom(), 0xF0));
    }

    private static int breathe(LoopFrame.Palette p, float phase) {
        float f = (Mth.sin(phase) + 1f) / 2f; // 0..1
        int a = p.edge(), b = p.glow();
        int r = lerp(a >> 16 & 0xFF, b >> 16 & 0xFF, f);
        int g = lerp(a >> 8 & 0xFF, b >> 8 & 0xFF, f);
        int bl = lerp(a & 0xFF, b & 0xFF, f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int lerp(int a, int b, float f) {
        return Math.round(a + (b - a) * f);
    }
}

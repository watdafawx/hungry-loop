package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.item.HungryLoopItem;
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

        boolean shiny = HungryLoopItem.contentsOf(event.getItemStack()).shiny();
        float t = (Util.getMillis() % 3000L) / 3000f * Mth.TWO_PI;

        event.setBorderStart(shimmer(t, shiny));
        event.setBorderEnd(shimmer(t + Mth.PI, shiny));
        event.setBackgroundStart(0xF00E0A14);
        event.setBackgroundEnd(0xF0130C1C);
    }

    private static int shimmer(float phase, boolean shiny) {
        float f = (Mth.sin(phase) + 1f) / 2f; // 0..1
        if (shiny) {
            int r = lerp(0x33, 0x8F, f), g = lerp(0x8C, 0xE6, f), b = lerp(0xA6, 0xFF, f);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        int r = lerp(0x6E, 0xE0, f), g = lerp(0x3F, 0x94, f), b = lerp(0x22, 0x50, f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float f) {
        return Math.round(a + (b - a) * f);
    }
}

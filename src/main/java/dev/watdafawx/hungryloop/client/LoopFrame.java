package dev.watdafawx.hungryloop.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The loop's frame, shared by its screen and its tooltip. Takes its cue from a Path of Exile unique
 * (The Hungry Loop is one there): a copper-brown header plate with ornate end caps and a gold title,
 * separators that fade out from the centre, an orange flavour line - and makes it hungry: the
 * border breathes with a slow ember glow that travels around it, the end caps are a ring of beads
 * turning like a snake eating its tail, and embers drift up through the body.
 *
 * <p>All procedural ({@code fill}/{@code fillGradient}), animated off wall-clock time so it runs at
 * the same pace whatever the frame rate. Every method draws inside the rectangle it is given.
 */
public final class LoopFrame {

    private LoopFrame() {}

    /** Colours for a normal (copper) or shiny (aqua) loop. */
    public record Palette(int bodyTop, int bodyBottom, int edgeDark, int edge, int edgeLight, int glow,
                          int plateTop, int plateBottom, int title, int flavour, int ember) {
        public static final Palette COPPER = new Palette(
                0xFF140D0B, 0xFF0A0706, 0xFF1E0E06, 0xFF8A4A22, 0xFFD98A4E, 0xFFFFA24A,
                0xFF5E2812, 0xFF35140A, 0xFFE3A35E, 0xFFAF6025, 0xFFFF8A3A);
        public static final Palette SHINY = new Palette(
                0xFF0B1214, 0xFF06090A, 0xFF06181E, 0xFF2A7486, 0xFF6FE0FF, 0xFF9AF0FF,
                0xFF124858, 0xFF0A2630, 0xFF9AF0FF, 0xFF4FB6CC, 0xFF6FE0FF);

        public static Palette of(boolean shiny) {
            return shiny ? SHINY : COPPER;
        }
    }

    /** Header plate height used by {@link #header}. */
    public static final int HEADER_H = 16;

    private static float seconds() {
        return (net.minecraft.Util.getMillis() % 3_600_000L) / 1000f;
    }

    // --- body + border ---------------------------------------------------------------------------

    public static void body(GuiGraphics g, int x, int y, int w, int h, Palette p) {
        g.fillGradient(x, y, x + w, y + h, p.bodyTop(), p.bodyBottom());
        embers(g, x, y, w, h, p);
        border(g, x, y, w, h, p);
    }

    /** Dark outer line, copper line, and a glow that breathes and travels around the edge. */
    public static void border(GuiGraphics g, int x, int y, int w, int h, Palette p) {
        float t = seconds();
        // outer dark + copper
        outline(g, x, y, w, h, p.edgeDark());
        outline(g, x + 1, y + 1, w - 2, h - 2, p.edge());
        // inner breathing highlight
        float breathe = (Mth.sin(t * 1.4f) + 1f) / 2f;
        outline(g, x + 2, y + 2, w - 4, h - 4, withAlpha(p.edgeLight(), 0x18 + Math.round(breathe * 0x30)));

        // a hot spot running clockwise along the copper line, with a short fading tail
        int perimeter = 2 * (w - 2) + 2 * (h - 2);
        int head = Math.floorMod((int) (t * 60), perimeter);
        for (int k = 0; k < 28; k++) {
            int a = Math.round(0xE0 * (1f - k / 28f));
            int[] pt = perimeterPoint(x + 1, y + 1, w - 2, h - 2, Math.floorMod(head - k, perimeter));
            g.fill(pt[0], pt[1], pt[0] + 1, pt[1] + 1, withAlpha(p.glow(), a));
        }
        corners(g, x, y, w, h, p, breathe);
    }

    private static void corners(GuiGraphics g, int x, int y, int w, int h, Palette p, float breathe) {
        int c = withAlpha(p.edgeLight(), 0x90 + Math.round(breathe * 0x60));
        int[][] at = {{x, y, 1, 1}, {x + w - 1, y, -1, 1}, {x, y + h - 1, 1, -1}, {x + w - 1, y + h - 1, -1, -1}};
        for (int[] a : at) {
            // small L-shaped bracket with a stud
            for (int i = 0; i < 6; i++) {
                px(g, a[0] + a[2] * i, a[1], c);
                px(g, a[0], a[1] + a[3] * i, c);
            }
            px(g, a[0] + a[2] * 3, a[1] + a[3] * 3, p.glow());
        }
    }

    /** Embers drifting up through the body: deterministic per index, so no state to keep. */
    private static void embers(GuiGraphics g, int x, int y, int w, int h, Palette p) {
        float t = seconds();
        int count = Math.max(6, w * h / 2400);
        for (int i = 0; i < count; i++) {
            float speed = 6f + (hash(i * 7 + 1) % 9);
            float life = h + 20;
            float rise = (t * speed + hash(i * 13 + 5) % (int) life) % life;
            int ex = x + 3 + (int) (hash(i * 31 + 3) % Math.max(1, w - 6))
                    + Math.round(Mth.sin(t * 0.9f + i) * 2f);
            int ey = y + h - 3 - (int) rise;
            if (ey < y + 3 || ey > y + h - 3) continue;
            float fade = Math.min(1f, rise / 30f) * Math.min(1f, (h - rise) / 40f);
            float flicker = 0.6f + 0.4f * ((Mth.sin(t * 6f + i * 1.7f) + 1f) / 2f);
            g.fill(ex, ey, ex + 1, ey + 1, withAlpha(p.ember(), Math.round(0x70 * fade * flicker)));
        }
    }

    // --- header ----------------------------------------------------------------------------------

    /**
     * The PoE-style title plate across the top of a panel, {@link #HEADER_H} tall, with turning
     * bead-ring end caps and the title centred in it.
     */
    public static void header(GuiGraphics g, Font font, int x, int y, int w, Component title, Palette p) {
        int px0 = x + 3, py0 = y + 3, px1 = x + w - 3, py1 = y + 3 + HEADER_H;
        g.fillGradient(px0, py0, px1, py1, p.plateTop(), p.plateBottom());
        // grain + top highlight + bottom shadow
        for (int yy = py0 + 3; yy < py1 - 2; yy += 3) g.fill(px0 + 12, yy, px1 - 12, yy + 1, 0x10000000);
        g.fill(px0, py0, px1, py0 + 1, withAlpha(p.edgeLight(), 0x70));
        g.fill(px0, py1 - 1, px1, py1, p.edgeDark());
        outline(g, px0 - 1, py0 - 1, px1 - px0 + 2, py1 - py0 + 2, p.edge());

        int capY = py0 + HEADER_H / 2;
        beadRing(g, px0 + 9, capY, p, false);
        beadRing(g, px1 - 10, capY, p, true);

        int tw = font.width(title);
        int tx = x + (w - tw) / 2;
        int ty = py0 + (HEADER_H - 8) / 2 + 1;
        g.drawString(font, title, tx + 1, ty + 1, 0xFF1A0A04, false);
        g.drawString(font, title, tx, ty, p.title(), false);
    }

    /**
     * Ten beads on a small circle, the brightest one chasing round: the loop eating its tail. The
     * right cap turns the other way, so the two read as a mirrored pair like PoE's ornaments.
     */
    private static void beadRing(GuiGraphics g, int cx, int cy, Palette p, boolean reverse) {
        float t = seconds() * (reverse ? -1.1f : 1.1f);
        g.fill(cx - 5, cy - 5, cx + 6, cy + 6, withAlpha(p.edgeDark(), 0xC0));
        int beads = 10;
        for (int i = 0; i < beads; i++) {
            float ang = t + i * Mth.TWO_PI / beads;
            int bx = cx + Math.round(Mth.cos(ang) * 4f);
            int by = cy + Math.round(Mth.sin(ang) * 4f);
            float head = i / (float) beads;
            int a = 0x50 + Math.round(head * 0xAF);
            px(g, bx, by, withAlpha(i == beads - 1 ? p.glow() : p.edgeLight(), a));
        }
        px(g, cx, cy, p.glow()); // the stone at the centre
    }

    // --- separators + flavour --------------------------------------------------------------------

    /** A 1px rule that is solid in the middle and fades out to both ends. */
    public static void separator(GuiGraphics g, int x, int y, int w, Palette p) {
        int half = w / 2;
        for (int i = 0; i < w; i++) {
            float d = Math.abs(i - half) / (float) half;
            int a = Math.round(0xC0 * (1f - d * d));
            if (a > 4) px(g, x + i, y, withAlpha(p.edge(), a));
        }
        px(g, x + half, y, p.edgeLight());
    }

    public static void flavour(GuiGraphics g, Font font, int cx, int y, Component text, Palette p) {
        g.drawCenteredString(font, text, cx, y, p.flavour());
    }

    // --- primitives ------------------------------------------------------------------------------

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    private static int[] perimeterPoint(int x, int y, int w, int h, int d) {
        if (d < w) return new int[]{x + d, y};
        d -= w;
        if (d < h) return new int[]{x + w - 1, y + d};
        d -= h;
        if (d < w) return new int[]{x + w - 1 - d, y + h - 1};
        d -= w;
        return new int[]{x, y + h - 1 - Math.min(d, h - 1)};
    }

    private static void px(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    private static int hash(int n) {
        n = (n ^ 61) ^ (n >>> 16);
        n *= 9;
        n ^= n >>> 4;
        n *= 0x27d4eb2d;
        n ^= n >>> 15;
        return n & 0x7FFFFFFF;
    }
}

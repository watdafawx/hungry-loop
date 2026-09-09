package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.menu.HungryLoopMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The loop's screen. Sub-slots trace a snake constellation; faint lines join every point on the
 * path (unlocked and locked) so the shape reads even when the loop is empty. Locked slots are
 * clickable to unlock, with the XP cost drawn beneath them and in a hover tooltip.
 */
public class HungryLoopScreen extends AbstractContainerScreen<HungryLoopMenu> {

    private static final int PANEL_TOP = 0xFF241C30;
    private static final int PANEL_BOTTOM = 0xFF15101E;
    private static final int COPPER = 0xFFB56A38;
    private static final int COPPER_LIGHT = 0xFFDD8B52;
    private static final int COPPER_DARK = 0xFF6E3F22;
    private static final int WELL = 0xFF0C0912;
    private static final int WELL_EDGE = 0xFF39303F;
    private static final int LINE_REAL = 0x66C89A6E;
    private static final int LINE_FUTURE = 0x2A8A7FA8;
    private static final int XP_FILL_TOP = 0xFF8FE84A;
    private static final int XP_FILL_BOTTOM = 0xFF4E9C25;
    private static final int SHINY = 0xFF6FE0FF;

    private int xpBarY;
    private int feedX, feedY, feedW, feedH;

    public HungryLoopScreen(HungryLoopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = HungryLoopMenu.PANEL_WIDTH;
        this.imageHeight = HungryLoopMenu.PANEL_HEIGHT;
        this.inventoryLabelY = HungryLoopMenu.PLAYER_INV_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 7;
        this.xpBarY = HungryLoopMenu.PLAYER_INV_Y - 34;
        this.feedW = 72;
        this.feedH = 16;
        this.feedX = leftPos + (imageWidth - feedW) / 2;
        this.feedY = topPos + xpBarY + 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        LoopContents data = menu.loopData();
        int total = menu.unlockedSlots();
        int cap = data.slotCap();
        int accent = data.shiny() ? SHINY : COPPER;
        int accentLight = data.shiny() ? SHINY : COPPER_LIGHT;
        int accentDark = data.shiny() ? 0xFF3C7C8C : COPPER_DARK;

        long time = net.minecraft.Util.getMillis();
        float breathe = (net.minecraft.util.Mth.sin(time / 1400f) + 1f) / 2f; // 0..1

        g.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        drawStarfield(g, x, y, time);
        bevel(g, x, y, imageWidth, imageHeight, accent, accentLight, accentDark);
        drawBorderSpark(g, x, y, time, accentLight);
        g.fill(x + 6, y + 18, x + imageWidth - 6, y + 19, accentDark);

        String count = data.totalSlots() + " / " + cap;
        g.drawString(font, count, x + imageWidth - 8 - font.width(count), y + 7,
                data.shiny() ? SHINY : 0xFFE7C9A6, false);

        // constellation lines, faintly breathing
        int lineReal = withAlpha(LINE_REAL, 0x40 + Math.round(breathe * 0x40));
        for (int i = 0; i + 1 < cap; i++) {
            int[] a = HungryLoopMenu.loopSlotCenter(i);
            int[] b = HungryLoopMenu.loopSlotCenter(i + 1);
            line(g, x + a[0], y + a[1], x + b[0], y + b[1], i + 1 < total ? lineReal : LINE_FUTURE);
        }
        drawConstellationPulse(g, x, y, time, Math.max(2, total), data.shiny() ? SHINY : 0xFFFFE7C0);

        int hovered = hoveredLockedSlot(mouseX, mouseY);

        for (int i = 0; i < cap; i++) {
            int sx = x + HungryLoopMenu.loopSlotX(i);
            int sy = y + HungryLoopMenu.loopSlotY(i);
            if (i < total) {
                well(g, sx, sy, WELL, WELL_EDGE);
            } else {
                boolean isHover = i == hovered;
                boolean isNext = i == total;
                int ring = isHover ? 0xFFE7C9A6 : isNext ? 0xFF7A6E4E : 0xFF3C3450;
                // dashed ring
                g.fill(sx + 2, sy, sx + 14, sy + 1, ring);
                g.fill(sx + 2, sy + 15, sx + 14, sy + 16, ring);
                g.fill(sx, sy + 2, sx + 1, sy + 14, ring);
                g.fill(sx + 15, sy + 2, sx + 16, sy + 14, ring);
                g.drawCenteredString(font, "+", sx + 8, sy + 4, ring);
                if (isNext || isHover) {
                    int cost = data.costToReach(i + 1);
                    boolean afford = menu.playerXpLevels() >= cost;
                    String c = String.valueOf(cost);
                    g.drawString(font, c, sx + 8 - font.width(c) / 2, sy + 17,
                            afford ? 0xFF7FDB3F : 0xFFD05A5A, false);
                }
            }
        }

        // XP bar
        int barX = x + 8;
        int barW = imageWidth - 16;
        g.fill(barX - 1, y + xpBarY - 1, barX + barW + 1, y + xpBarY + 8, 0xFF000000);
        g.fill(barX, y + xpBarY, barX + barW, y + xpBarY + 7, WELL);
        int fillW = Math.round(barW * data.growthProgress());
        if (fillW > 0) {
            g.fillGradient(barX, y + xpBarY, barX + fillW, y + xpBarY + 7, XP_FILL_TOP, XP_FILL_BOTTOM);
        }
        String barText = data.canGrow()
                ? data.storedXp() + " / " + data.nextSlotCost() + " XP levels"
                : (data.canRaiseCap() ? "Feed a Nether Star to widen" : "Fully fed");
        g.drawCenteredString(font, barText, x + imageWidth / 2, y + xpBarY - 1, 0xFFFFFFFF);

        // feed button
        boolean feedHover = inFeed(mouseX, mouseY);
        boolean star = menu.getCarried().is(Items.NETHER_STAR);
        g.fill(feedX - 1, feedY - 1, feedX + feedW + 1, feedY + feedH + 1, 0xFF000000);
        g.fillGradient(feedX, feedY, feedX + feedW, feedY + feedH,
                feedHover ? 0xFF3A2E22 : 0xFF2A2018, feedHover ? 0xFF241C14 : 0xFF1C150F);
        bevel(g, feedX, feedY, feedW, feedH, star ? 0xFFB06CC0 : COPPER,
                star ? 0xFFD79CE0 : COPPER_LIGHT, COPPER_DARK);
        g.drawCenteredString(font, star ? "Widen" : "Feed", feedX + feedW / 2, feedY + 4,
                feedHover ? 0xFFFFE9C9 : 0xFFE7C9A6);

        // player inventory wells
        for (Slot slot : menu.slots) {
            if (slot.container != menu.contentsContainer()) {
                well(g, x + slot.x, y + slot.y, WELL, WELL_EDGE);
            }
        }
    }

    // --- hit testing ---------------------------------------------------------------------------

    private boolean inFeed(double mx, double my) {
        return mx >= feedX && mx < feedX + feedW && my >= feedY && my < feedY + feedH;
    }

    private int hoveredLockedSlot(double mouseX, double mouseY) {
        LoopContents data = menu.loopData();
        for (int i = menu.unlockedSlots(); i < data.slotCap(); i++) {
            int sx = leftPos + HungryLoopMenu.loopSlotX(i);
            int sy = topPos + HungryLoopMenu.loopSlotY(i);
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) return i;
        }
        return -1;
    }

    private void send(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inFeed(mouseX, mouseY)) {
                boolean star = menu.getCarried().is(Items.NETHER_STAR);
                send(star ? HungryLoopMenu.BUTTON_STAR
                        : Screen.hasShiftDown() ? HungryLoopMenu.BUTTON_FEED_ALL : HungryLoopMenu.BUTTON_FEED_ONE);
                return true;
            }
            int locked = hoveredLockedSlot(mouseX, mouseY);
            if (locked >= 0) {
                send(HungryLoopMenu.BUTTON_SLOT_BASE + locked);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // renders bg + slots (not tooltips)
        renderTooltip(g, mouseX, mouseY);             // AbstractContainerScreen leaves this to us
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        LoopContents data = menu.loopData();

        int locked = hoveredLockedSlot(mouseX, mouseY);
        if (locked >= 0) {
            int cost = data.costToReach(locked + 1);
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("hungryloop.slot.locked", locked + 1).withStyle(ChatFormatting.GOLD),
                    Component.translatable("hungryloop.slot.cost", cost).withStyle(
                            menu.playerXpLevels() >= cost ? ChatFormatting.GREEN : ChatFormatting.RED),
                    Component.translatable("hungryloop.slot.click").withStyle(ChatFormatting.DARK_GRAY)
            ), mouseX, mouseY);
            return;
        }

        if (inFeed(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("hungryloop.feed.click").withStyle(ChatFormatting.GREEN),
                    Component.translatable("hungryloop.feed.shift").withStyle(ChatFormatting.GRAY),
                    Component.translatable("hungryloop.feed.star").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("hungryloop.feed.have", menu.playerXpLevels()).withStyle(ChatFormatting.DARK_GRAY)
            ), mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    // --- drawing helpers --------------------------------------------------------------------

    // --- animation ------------------------------------------------------------------------------

    private static int withAlpha(int argb, int alpha) {
        return (Math.min(0xFF, alpha) << 24) | (argb & 0xFFFFFF);
    }

    /** Faint dots drifting slowly upward, wrapping - a subtle nebula behind the panel. */
    private void drawStarfield(GuiGraphics g, int x, int y, long time) {
        int inX = x + 3, inY = y + 20, w = imageWidth - 6, h = HungryLoopMenu.PLAYER_INV_Y - 44;
        for (int i = 0; i < 22; i++) {
            int seedX = (int) ((i * 2654435761L >>> 8 & 0xFFFF) % w);
            float speed = 0.004f + (i % 5) * 0.0015f;
            int py = (int) (h - ((time * speed + i * 37) % h));
            int alpha = 0x14 + (i % 3) * 0x0C;
            g.fill(inX + seedX, inY + py, inX + seedX + 1, inY + py + 1, (alpha << 24) | 0xC9C0E0);
        }
    }

    /** A bright point travelling the panel's inner border. */
    private void drawBorderSpark(GuiGraphics g, int x, int y, long time, int color) {
        int w = imageWidth - 4, h = imageHeight - 4;
        int perim = 2 * (w + h);
        int p = (int) ((time / 6) % perim);
        int sx, sy;
        if (p < w) { sx = x + 2 + p; sy = y + 2; }
        else if (p < w + h) { sx = x + w + 1; sy = y + 2 + (p - w); }
        else if (p < 2 * w + h) { sx = x + w + 1 - (p - w - h); sy = y + h + 1; }
        else { sx = x + 2; sy = y + h + 1 - (p - 2 * w - h); }
        g.fill(sx - 1, sy - 1, sx + 2, sy + 2, withAlpha(color, 0xB0));
        g.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
    }

    /** A glowing head with a fading tail, running along the unlocked stretch of the constellation. */
    private void drawConstellationPulse(GuiGraphics g, int x, int y, long time, int nodes, int color) {
        if (nodes < 2) return;
        float head = (time % 5000L) / 5000f * (nodes - 1);
        for (int trail = 0; trail < 5; trail++) {
            float at = head - trail * 0.45f;
            if (at < 0) continue;
            int lo = (int) Math.floor(at);
            float frac = at - lo;
            int[] a = HungryLoopMenu.loopSlotCenter(lo);
            int[] b = HungryLoopMenu.loopSlotCenter(Math.min(lo + 1, nodes - 1));
            int px = x + Math.round(a[0] + (b[0] - a[0]) * frac);
            int py = y + Math.round(a[1] + (b[1] - a[1]) * frac);
            int alpha = 0xC0 - trail * 0x28;
            int size = trail == 0 ? 2 : 1;
            g.fill(px - size, py - size, px + size + 1, py + size + 1, withAlpha(color, Math.max(0, alpha)));
        }
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    private static void well(GuiGraphics g, int x, int y, int fill, int edge) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF000000);
        g.fill(x, y, x + 16, y + 16, fill);
        g.fill(x, y, x + 16, y + 1, edge);
        g.fill(x, y, x + 1, y + 16, edge);
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int base, int light, int dark) {
        g.fill(x, y, x + w, y + 1, light);
        g.fill(x, y, x + 1, y + h, light);
        g.fill(x, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y, x + w, y + h, dark);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, base);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, base);
    }
}

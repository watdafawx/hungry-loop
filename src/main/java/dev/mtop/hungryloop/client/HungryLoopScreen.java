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

/**
 * The loop's screen. The sub-slots trace a snake constellation - a serpentine path with a gentle
 * undulation - and faint lines join every point on the path (occupied and not) so the shape reads
 * even on an empty loop.
 */
public class HungryLoopScreen extends AbstractContainerScreen<HungryLoopMenu> {

    private static final int PANEL_TOP = 0xFF241C30;
    private static final int PANEL_BOTTOM = 0xFF15101E;
    private static final int COPPER = 0xFFB56A38;
    private static final int COPPER_LIGHT = 0xFFDD8B52;
    private static final int COPPER_DARK = 0xFF6E3F22;
    private static final int WELL = 0xFF0C0912;
    private static final int WELL_EDGE = 0xFF39303F;
    private static final int NODE_FUTURE = 0xFF2C2440;
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
        int total = menu.loopSlotCount();
        int cap = data.slotCap();
        int accent = data.shiny() ? SHINY : COPPER;
        int accentLight = data.shiny() ? SHINY : COPPER_LIGHT;
        int accentDark = data.shiny() ? 0xFF3C7C8C : COPPER_DARK;

        g.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        bevel(g, x, y, imageWidth, imageHeight, accent, accentLight, accentDark);
        g.fill(x + 6, y + 18, x + imageWidth - 6, y + 19, accentDark);

        String count = data.totalSlots() + " / " + cap;
        g.drawString(font, count, x + imageWidth - 8 - font.width(count), y + 7,
                data.shiny() ? SHINY : 0xFFE7C9A6, false);

        // constellation lines through every point on the path
        for (int i = 0; i + 1 < cap; i++) {
            int[] a = HungryLoopMenu.loopSlotCenter(i);
            int[] b = HungryLoopMenu.loopSlotCenter(i + 1);
            boolean real = i + 1 < total;
            line(g, x + a[0], y + a[1], x + b[0], y + b[1], real ? LINE_REAL : LINE_FUTURE);
        }

        // nodes
        for (int i = 0; i < cap; i++) {
            int sx = x + HungryLoopMenu.loopSlotX(i);
            int sy = y + HungryLoopMenu.loopSlotY(i);
            if (i < total) {
                well(g, sx, sy, WELL, WELL_EDGE);
            } else {
                g.fill(sx + 5, sy + 5, sx + 11, sy + 11, NODE_FUTURE);
                g.drawCenteredString(font, "+", sx + 8, sy + 4, 0xFF433A55);
            }
        }
        // head marker on slot 0
        if (cap > 0) {
            int[] h = HungryLoopMenu.loopSlotCenter(0);
            g.fill(x + h[0] - 1, y + h[1] - 10, x + h[0] + 1, y + h[1] - 8, accent);
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
                : (data.canRaiseCap(24) ? "Feed a Nether Star to widen" : "Fully fed");
        g.drawCenteredString(font, barText, x + imageWidth / 2, y + xpBarY - 1, 0xFFFFFFFF);

        // feed button
        boolean hover = mouseX >= feedX && mouseX < feedX + feedW && mouseY >= feedY && mouseY < feedY + feedH;
        g.fill(feedX - 1, feedY - 1, feedX + feedW + 1, feedY + feedH + 1, 0xFF000000);
        g.fillGradient(feedX, feedY, feedX + feedW, feedY + feedH,
                hover ? 0xFF3A2E22 : 0xFF2A2018, hover ? 0xFF241C14 : 0xFF1C150F);
        bevel(g, feedX, feedY, feedW, feedH, COPPER, COPPER_LIGHT, COPPER_DARK);
        g.drawCenteredString(font, "Feed", feedX + feedW / 2, feedY + 4, hover ? 0xFFFFE9C9 : 0xFFE7C9A6);

        // player inventory wells
        for (int s = total; s < menu.slots.size(); s++) {
            Slot slot = menu.slots.get(s);
            well(g, x + slot.x, y + slot.y, WELL, WELL_EDGE);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= feedX && mouseX < feedX + feedW && mouseY >= feedY && mouseY < feedY + feedH) {
            int id = Screen.hasShiftDown() ? HungryLoopMenu.BUTTON_FEED_ALL : HungryLoopMenu.BUTTON_FEED_ONE;
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                        .forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX >= feedX && mouseX < feedX + feedW && mouseY >= feedY && mouseY < feedY + feedH) {
            g.renderComponentTooltip(font, java.util.List.of(
                    Component.translatable("hungryloop.feed.click").withStyle(ChatFormatting.GREEN),
                    Component.translatable("hungryloop.feed.shift").withStyle(ChatFormatting.GRAY),
                    Component.translatable("hungryloop.feed.star").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("hungryloop.feed.have", menu.playerXpLevels()).withStyle(ChatFormatting.DARK_GRAY)
            ), mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}

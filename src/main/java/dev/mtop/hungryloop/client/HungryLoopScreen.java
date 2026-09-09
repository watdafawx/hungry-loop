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
 * The loop's screen. Drawn procedurally - obsidian panel, copper bevel, an iridescent divider that
 * echoes the ring band, a grid of sub-slots (real + dimmed "future" slots up to the cap), and an
 * XP-feed bar + button for growing the loop.
 */
public class HungryLoopScreen extends AbstractContainerScreen<HungryLoopMenu> {

    // palette
    private static final int PANEL_TOP = 0xFF221B2B;
    private static final int PANEL_BOTTOM = 0xFF181320;
    private static final int BLACK = 0xFF000000;
    private static final int COPPER = 0xFFB56A38;
    private static final int COPPER_LIGHT = 0xFFDD8B52;
    private static final int COPPER_DARK = 0xFF6E3F22;
    private static final int WELL = 0xFF0C0912;
    private static final int WELL_EDGE = 0xFF39303F;
    private static final int GHOST_WELL = 0xFF15111C;
    private static final int GHOST_MARK = 0xFF2E2740;
    private static final int XP_FILL_TOP = 0xFF8FE84A;
    private static final int XP_FILL_BOTTOM = 0xFF4E9C25;
    private static final int SHINY = 0xFF6FE0FF;

    private static final int[] IRIDESCENT = {0xFFD0605A, 0xFFE4E8F2, 0xFF9FC8F0};

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
        this.xpBarY = HungryLoopMenu.LOOP_ORIGIN_Y + 2 * 18 + 8;
        this.feedW = 64;
        this.feedH = 16;
        this.feedX = leftPos + (imageWidth - feedW) / 2;
        this.feedY = topPos + xpBarY + 12;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        LoopContents data = menu.loopData();

        // panel + bevel
        g.fill(x - 4, y - 4, x + imageWidth + 4, y + imageHeight + 4, BLACK);
        g.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        bevel(g, x, y, imageWidth, imageHeight, data.shiny() ? SHINY : COPPER,
                data.shiny() ? SHINY : COPPER_LIGHT, data.shiny() ? 0xFF3C7C8C : COPPER_DARK);

        // title-bar underline
        g.fill(x + 6, y + 18, x + imageWidth - 6, y + 19, COPPER_DARK);

        // slot count badge, top-right
        String count = data.totalSlots() + " / " + LoopContents.MAX_TOTAL_SLOTS;
        g.drawString(font, count, x + imageWidth - 8 - font.width(count), y + 7,
                data.shiny() ? SHINY : 0xFFE7C9A6, false);

        // loop grid: real wells, then dim "future" wells up to the cap
        int total = menu.loopSlotCount();
        for (int i = 0; i < LoopContents.MAX_TOTAL_SLOTS; i++) {
            int sx = x + HungryLoopMenu.loopSlotX(i, Math.max(total, i + 1));
            int sy = y + HungryLoopMenu.loopSlotY(i);
            if (i < total) {
                well(g, sx, sy, WELL, WELL_EDGE);
            } else {
                well(g, sx, sy, GHOST_WELL, GHOST_WELL);
                g.drawCenteredString(font, "+", sx + 8, sy + 4, GHOST_MARK);
            }
        }

        // iridescent divider under the grid
        int divY = y + HungryLoopMenu.LOOP_ORIGIN_Y + 2 * 18 + 1;
        for (int px = x + 7; px < x + imageWidth - 7; px += 3) {
            g.fill(px, divY, px + 2, divY + 1, IRIDESCENT[((px - x) / 3) % IRIDESCENT.length]);
        }

        // XP bar
        int barX = x + 8;
        int barW = imageWidth - 16;
        g.fill(barX - 1, y + xpBarY - 1, barX + barW + 1, y + xpBarY + 8, BLACK);
        g.fill(barX, y + xpBarY, barX + barW, y + xpBarY + 7, WELL);
        int fillW = Math.round(barW * data.growthProgress());
        if (fillW > 0) {
            g.fillGradient(barX, y + xpBarY, barX + fillW, y + xpBarY + 7, XP_FILL_TOP, XP_FILL_BOTTOM);
        }
        String barText = data.canGrow()
                ? data.storedXp() + " / " + data.nextSlotCost() + " XP levels"
                : "Fully fed";
        g.drawCenteredString(font, barText, x + imageWidth / 2, y + xpBarY - 1, 0xFFFFFFFF);

        // feed button
        if (data.canGrow()) {
            boolean hover = mouseX >= feedX && mouseX < feedX + feedW && mouseY >= feedY && mouseY < feedY + feedH;
            g.fill(feedX - 1, feedY - 1, feedX + feedW + 1, feedY + feedH + 1, BLACK);
            g.fillGradient(feedX, feedY, feedX + feedW, feedY + feedH,
                    hover ? 0xFF3A2E22 : 0xFF2A2018, hover ? 0xFF241C14 : 0xFF1C150F);
            bevel(g, feedX, feedY, feedW, feedH, COPPER, COPPER_LIGHT, COPPER_DARK);
            g.drawCenteredString(font, "Feed XP", feedX + feedW / 2, feedY + 4,
                    hover ? 0xFFFFE9C9 : 0xFFE7C9A6);
        }

        // player inventory wells
        for (Slot slot : menu.slots) {
            if (menu.slots.indexOf(slot) < total) continue;
            well(g, x + slot.x, y + slot.y, WELL, WELL_EDGE);
        }
    }

    private static void well(GuiGraphics g, int x, int y, int fill, int edge) {
        g.fill(x - 1, y - 1, x + 17, y + 17, BLACK);
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
        if (button == 0 && menu.loopData().canGrow()
                && mouseX >= feedX && mouseX < feedX + feedW && mouseY >= feedY && mouseY < feedY + feedH) {
            int id = Screen.hasShiftDown() ? HungryLoopMenu.BUTTON_FEED_SLOT : HungryLoopMenu.BUTTON_FEED_ONE;
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
        if (menu.loopData().canGrow()
                && mouseX >= feedX && mouseX < feedX + feedW && mouseY >= feedY && mouseY < feedY + feedH) {
            g.renderComponentTooltip(font, java.util.List.of(
                    Component.translatable("hungryloop.feed.click").withStyle(ChatFormatting.GREEN),
                    Component.translatable("hungryloop.feed.shift").withStyle(ChatFormatting.GRAY),
                    Component.translatable("hungryloop.feed.have", menu.playerXpLevels()).withStyle(ChatFormatting.DARK_GRAY)
            ), mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}

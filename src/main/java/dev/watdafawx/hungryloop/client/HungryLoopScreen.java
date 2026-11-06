package dev.watdafawx.hungryloop.client;

import dev.watdafawx.hungryloop.component.LoopContents;
import dev.watdafawx.hungryloop.menu.HungryLoopMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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

    /** Master cosmetics eye, inside the header plate right of the left end cap. */
    private static final int EYE_X = 22;
    private static final int EYE_Y = 8;

    private int xpBarY;
    private int feedX, feedY, feedW, feedH;

    public HungryLoopScreen(HungryLoopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = HungryLoopMenu.PANEL_WIDTH;
        this.imageHeight = HungryLoopMenu.PANEL_HEIGHT;
        this.inventoryLabelX = HungryLoopMenu.PLAYER_INV_X;
        this.inventoryLabelY = HungryLoopMenu.PLAYER_INV_Y - 11;
    }

    /** Right column, beside the ring: XP bar, feed button, flavour. */
    private static final int COL_X = HungryLoopMenu.PLAYER_INV_X;
    private static final int COL_W = 9 * 18;

    @Override
    protected void init() {
        super.init();
        this.xpBarY = 30;
        this.feedW = 72;
        this.feedH = 16;
        this.feedX = leftPos + COL_X + (COL_W - feedW) / 2;
        this.feedY = topPos + 46;
        layoutRing();
    }

    /**
     * Everything that must fit, checked by {@code SelfTest}'s scales scenario: the panel inside the
     * GUI, and the ring slots (with their eye and cost label), feed button and inventory inside the
     * panel. Returns what doesn't.
     */
    public List<String> layoutProblems(int guiWidth, int guiHeight) {
        List<String> out = new java.util.ArrayList<>();
        if (leftPos < 0 || topPos < 0 || leftPos + imageWidth > guiWidth || topPos + imageHeight > guiHeight) {
            out.add("panel " + imageWidth + "x" + imageHeight + " at " + leftPos + "," + topPos
                    + " doesn't fit gui " + guiWidth + "x" + guiHeight);
        }
        int n = ringCount();
        for (int i = 0; i < n; i++) {
            int sx = ringX(i), sy = ringY(i);
            // slot, its eye (x+10..x+17, y-3..y+4) and its cost label (about 15px outward)
            if (sx - 16 < 0 || sy - 16 < LoopFrame.HEADER_H + 4 || sx + 32 > COL_X - 2 || sy + 32 > imageHeight) {
                out.add("ring slot " + i + " (with eye/cost label) at " + sx + "," + sy + " leaves the ring area");
            }
        }
        if (feedX + feedW > leftPos + imageWidth || feedY + feedH > topPos + imageHeight) out.add("feed button outside panel");
        for (Slot slot : menu.slots) {
            if (slot.container != menu.contentsContainer() && (slot.x + 18 > imageWidth || slot.y + 18 > imageHeight)) {
                out.add("inventory slot at " + slot.x + "," + slot.y + " outside panel");
            }
        }
        int flavourLines = font.split(Component.translatable("hungryloop.flavour"), COL_W - 4).size();
        if (flavourLines > 2) out.add("flavour text needs " + flavourLines + " lines, room for 2");
        return out;
    }

    /** How many slots the ring shows: the capacity, or the contents if they overrun it. */
    private int ringCount() {
        LoopContents data = menu.loopData();
        return Math.min(HungryLoopMenu.MAX_LOOP_SLOTS, Math.max(Math.max(1, data.slotCap()), data.items().size()));
    }

    private int ringX(int i) {
        return HungryLoopMenu.ringSlotX(i, ringCount());
    }

    private int ringY(int i) {
        return HungryLoopMenu.ringSlotY(i, ringCount());
    }

    /**
     * Put the loop's menu slots on the ring for the current capacity. Presentation only (positions,
     * never the slot list), so it can run every frame and follows the cap live when it grows.
     */
    private void layoutRing() {
        int n = ringCount();
        for (int i = 0; i < HungryLoopMenu.MAX_LOOP_SLOTS && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (i < n) {
                slot.x = HungryLoopMenu.ringSlotX(i, n);
                slot.y = HungryLoopMenu.ringSlotY(i, n);
            } else {
                slot.x = -10000;
                slot.y = -10000;
            }
        }
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

        LoopFrame.Palette frame = LoopFrame.Palette.of(data.shiny());
        LoopFrame.body(g, x, y, imageWidth, imageHeight, frame);
        LoopFrame.header(g, font, x, y, imageWidth, title, frame);

        // slot count + master cosmetics toggle sit inside the plate, between the end caps
        String count = data.totalSlots() + "/" + cap;
        g.drawString(font, count, x + imageWidth - 22 - font.width(count), y + 7, frame.title(), false);
        drawEye(g, EYE_X + x, EYE_Y + y, data.selfRenders(), inEye(mouseX, mouseY, x + EYE_X, y + EYE_Y));

        layoutRing();
        int n = ringCount();

        // the ring itself: a closed path through every slot, the unlocked arc brighter
        int lineReal = withAlpha(frame.edge(), 0x70 + Math.round(breathe * 0x50));
        int lineFuture = withAlpha(frame.edge(), 0x28);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            boolean real = j == 0 ? total >= n : j < total;
            line(g, x + ringX(i) + 8, y + ringY(i) + 8, x + ringX(j) + 8, y + ringY(j) + 8, real ? lineReal : lineFuture);
        }
        drawRingPulse(g, x, y, time, Math.max(2, total), frame.glow());

        // the wearer, standing in the loop
        drawWearer(g, x, y, mouseX, mouseY);

        int hovered = hoveredLockedSlot(mouseX, mouseY);

        for (int i = 0; i < n; i++) {
            int sx = x + ringX(i);
            int sy = y + ringY(i);
            boolean overflow = i >= total && i < data.items().size() && !data.items().get(i).isEmpty();
            if (i < total || overflow) {
                well(g, sx, sy, WELL, withAlpha(frame.edge(), 0x90));
                if (i < data.items().size() && !data.items().get(i).isEmpty()) {
                    drawEye(g, sx + 10, sy - 3, data.rendersItem(i), inEye(mouseX, mouseY, sx + 10, sy - 3));
                }
            } else {
                boolean isHover = i == hovered;
                boolean isNext = i == total;
                int ring = isHover ? 0xFFE7C9A6 : isNext ? withAlpha(frame.edgeLight(), 0xD0) : 0xFF3C3028;
                g.fill(sx + 2, sy, sx + 14, sy + 1, ring);
                g.fill(sx + 2, sy + 15, sx + 14, sy + 16, ring);
                g.fill(sx, sy + 2, sx + 1, sy + 14, ring);
                g.fill(sx + 15, sy + 2, sx + 16, sy + 14, ring);
                g.drawCenteredString(font, "+", sx + 8, sy + 4, ring);
                if (isNext || isHover) {
                    int cost = data.costToReach(i + 1);
                    boolean afford = menu.playerXpLevels() >= cost;
                    String c = String.valueOf(cost);
                    // cost label just outside the ring, away from the wearer
                    double ang = Math.atan2(ringY(i) + 8 - HungryLoopMenu.RING_CY, ringX(i) + 8 - HungryLoopMenu.RING_CX);
                    int lx = sx + 8 + (int) Math.round(Math.cos(ang) * 15);
                    int ly = sy + 4 + (int) Math.round(Math.sin(ang) * 15);
                    g.drawString(font, c, lx - font.width(c) / 2, ly, afford ? 0xFF7FDB3F : 0xFFD05A5A, false);
                }
            }
        }

        // XP bar
        int barX = x + COL_X;
        int barW = COL_W;
        g.fill(barX - 1, y + xpBarY - 1, barX + barW + 1, y + xpBarY + 10, 0xFF000000);
        g.fill(barX, y + xpBarY, barX + barW, y + xpBarY + 9, WELL);
        int fillW = Math.round(barW * data.growthProgress());
        if (fillW > 0) {
            g.fillGradient(barX, y + xpBarY, barX + fillW, y + xpBarY + 9, XP_FILL_TOP, XP_FILL_BOTTOM);
        }
        String barText = data.canGrow()
                ? data.storedXp() + " / " + data.nextSlotCost() + " XP levels"
                : (data.canRaiseCap() ? "Feed a Nether Star to widen" : "Fully fed");
        g.drawCenteredString(font, barText, barX + barW / 2, y + xpBarY + 1, 0xFFFFFFFF);

        // flavour, framed by rules that fade out from the centre. Full-size font wrapped to the column
        // (at most two lines) rather than scaled down - a 75% font is unreadable at GUI scale 1.
        LoopFrame.separator(g, x + COL_X, y + 65, COL_W, frame);
        var lines = font.split(Component.translatable("hungryloop.flavour").withStyle(ChatFormatting.ITALIC), COL_W - 4);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), x + COL_X + (COL_W - font.width(lines.get(i))) / 2, y + 69 + i * 9, frame.flavour(), false);
        }
        LoopFrame.separator(g, x + COL_X, y + 87, COL_W, frame);

        // feed button
        boolean feedHover = inFeed(mouseX, mouseY);
        boolean star = menu.getCarried().is(Items.NETHER_STAR);
        g.fill(feedX - 1, feedY - 1, feedX + feedW + 1, feedY + feedH + 1, 0xFF000000);
        g.fillGradient(feedX, feedY, feedX + feedW, feedY + feedH,
                feedHover ? 0xFF3A2E22 : 0xFF2A2018, feedHover ? 0xFF241C14 : 0xFF1C150F);
        bevel(g, feedX, feedY, feedW, feedH, star ? 0xFFB06CC0 : frame.edge(),
                star ? 0xFFD79CE0 : frame.edgeLight(), frame.edgeDark());
        g.drawCenteredString(font, star ? "Widen" : "Feed", feedX + feedW / 2, feedY + 4,
                feedHover ? 0xFFFFE9C9 : 0xFFE7C9A6);

        // player inventory wells
        for (Slot slot : menu.slots) {
            if (slot.container != menu.contentsContainer()) {
                well(g, x + slot.x, y + slot.y, WELL, withAlpha(frame.edge(), 0x60));
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
            int sx = leftPos + ringX(i);
            int sy = topPos + ringY(i);
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
            int eye = eyeButtonAt(mouseX, mouseY);
            if (eye >= 0) {
                send(eye);
                return true;
            }
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

    /** The title lives in the header plate (LoopFrame.header); only the inventory label is drawn here. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFAF8A66, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (LoopOverlay.hasParent()) {
            LoopOverlay.renderBackdrop(g, partialTick);
            // AbstractContainerScreen.renderBackground is also what draws the panel - keep that.
            renderBg(g, partialTick, mouseX, mouseY);
        } else {
            super.renderBackground(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int width, int height) {
        LoopOverlay.resize(mc, width, height);
        super.resize(mc, width, height);
    }

    @Override
    public void onClose() {
        super.onClose();
        LoopOverlay.returnToParent();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // renders bg + slots (not tooltips)
        renderTooltip(g, mouseX, mouseY);             // AbstractContainerScreen leaves this to us
    }

    /**
     * The wearer stands in the middle of the ring on a dark disc - the loop is around them, and the
     * contained cosmetics show on the model as they would in game.
     */
    private void drawWearer(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        int cx = x + HungryLoopMenu.RING_CX;
        int cy = y + HungryLoopMenu.RING_CY;
        int rd = Math.max(26, HungryLoopMenu.ringRadius(ringCount()) - 18);
        for (int dy = -rd; dy <= rd; dy++) {
            int half = (int) Math.round(Math.sqrt(rd * rd - dy * dy));
            float f = Math.abs(dy) / (float) rd;
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, withAlpha(0x000000, 0x60 + Math.round((1 - f) * 0x50)));
        }
        int h = Math.min(rd * 2 - 6, 84);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                cx - rd, cy - h / 2, cx + rd, cy + h / 2, Math.max(18, h * 3 / 8), 0.0625f, mouseX, mouseY, minecraft.player);
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

    /**
     * A contained item's tooltip says which slot it counts as while the loop is worn. Empty slots have
     * no type (it comes from the item put in them), and a filled slot is covered by its item, so the
     * tooltip is the one place this is always both visible and true. Uses the same resolution as the
     * mirror, and Curios' own slot names.
     */
    @Override
    protected List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot == null || hoveredSlot.index >= HungryLoopMenu.MAX_LOOP_SLOTS || minecraft == null
                || minecraft.player == null || stack.getItem() instanceof dev.watdafawx.hungryloop.item.HungryLoopItem) {
            return lines;
        }
        String type = dev.watdafawx.hungryloop.curio.ContentForwarding.resolveSlot(stack, minecraft.player);
        List<Component> out = new java.util.ArrayList<>(lines);
        out.add(1, Component.translatable("hungryloop.tooltip.acts_as",
                Component.translatable("curios.identifier." + type)).withStyle(ChatFormatting.DARK_AQUA));
        return out;
    }

    // --- drawing helpers --------------------------------------------------------------------

    // --- render-toggle eyes -------------------------------------------------------------------

    private static final int EYE = 7;

    private boolean inEye(double mx, double my, int ex, int ey) {
        return mx >= ex && mx < ex + EYE && my >= ey && my < ey + EYE;
    }

    private static void drawEye(GuiGraphics g, int ex, int ey, boolean open, boolean hover) {
        g.fill(ex - 1, ey - 1, ex + EYE + 1, ey + EYE + 1, 0xC0000000);
        int col = open ? (hover ? 0xFF9CF0FA : 0xFF6FD8E0) : (hover ? 0xFFB0A0A0 : 0xFF706868);
        // lens
        g.fill(ex, ey + 2, ex + EYE, ey + 5, col);
        g.fill(ex + 1, ey + 1, ex + EYE - 1, ey + 6, col);
        if (open) {
            g.fill(ex + 2, ey + 2, ex + 5, ey + 5, 0xFF0C0912);
            g.fill(ex + 3, ey + 3, ex + 4, ey + 4, 0xFFFFFFFF);
        } else {
            g.fill(ex, ey + 3, ex + EYE, ey + 4, 0xFF17131E); // slash through
        }
    }

    /** Eye clicked over a loop slot, or the master eye. Returns the button id, or -1. */
    private int eyeButtonAt(double mx, double my) {
        LoopContents data = menu.loopData();
        if (inEye(mx, my, leftPos + EYE_X, topPos + EYE_Y)) return HungryLoopMenu.BUTTON_RENDER_SELF;
        for (int i = 0; i < menu.unlockedSlots() && i < data.items().size(); i++) {
            if (data.items().get(i).isEmpty()) continue;
            int ex = leftPos + ringX(i) + 10;
            int ey = topPos + ringY(i) - 3;
            if (inEye(mx, my, ex, ey)) return HungryLoopMenu.BUTTON_RENDER_BASE + i;
        }
        return -1;
    }

    // --- animation ------------------------------------------------------------------------------

    private static int withAlpha(int argb, int alpha) {
        return (Math.min(0xFF, alpha) << 24) | (argb & 0xFFFFFF);
    }

    /** A glowing head with a fading tail, running round the unlocked arc of the ring. */
    private void drawRingPulse(GuiGraphics g, int x, int y, long time, int nodes, int color) {
        boolean closed = nodes >= ringCount(); // full ring unlocked: the pulse can wrap around
        float span = closed ? nodes : nodes - 1;
        float head = (time % 6000L) / 6000f * span;
        for (int trail = 0; trail < 6; trail++) {
            float at = head - trail * 0.35f;
            if (at < 0) {
                if (!closed) continue;
                at += nodes;
            }
            int lo = (int) Math.floor(at) % ringCount();
            int hi = (lo + 1) % ringCount();
            float frac = at - (float) Math.floor(at);
            int ax = ringX(lo) + 8, ay = ringY(lo) + 8, bx = ringX(hi) + 8, by = ringY(hi) + 8;
            int px = x + Math.round(ax + (bx - ax) * frac);
            int py = y + Math.round(ay + (by - ay) * frac);
            int size = trail == 0 ? 1 : 0;
            g.fill(px - size, py - size, px + size + 1, py + size + 1, withAlpha(color, Math.max(0, 0xD0 - trail * 0x22)));
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

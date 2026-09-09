package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.menu.HungryLoopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for {@link HungryLoopMenu}. Drawn without a background texture - the panel and every slot
 * cell are filled at the coordinates of the menu's own slots, so the art always lines up whatever
 * the loop's slot count.
 */
public class HungryLoopScreen extends AbstractContainerScreen<HungryLoopMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_EDGE = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    public HungryLoopScreen(HungryLoopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 18 + 3 * 18 + 14 + 4 * 18 + 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, PANEL_EDGE);
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);

        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BORDER);
            g.fill(sx, sy, sx + 16, sy + 16, SLOT_FILL);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}

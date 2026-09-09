package dev.mtop.hungryloop.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Where a specific Hungry Loop lives on a player, in a form that survives opening a new menu (which
 * discards the slot the click came from) and a round trip to the client.
 *
 * <p>Resolved back to the live {@link ItemStack} on demand, so writes to the loop's component land
 * on the real stack wherever it sits - held, in the inventory, or equipped in a curio slot.
 */
public record LoopLocator(Kind kind, int index, String curioId) {

    public enum Kind { HAND_MAIN, HAND_OFF, INVENTORY, CURIO }

    public static LoopLocator hand(InteractionHand hand) {
        return new LoopLocator(hand == InteractionHand.MAIN_HAND ? Kind.HAND_MAIN : Kind.HAND_OFF, 0, "");
    }

    public static LoopLocator inventory(int slot) {
        return new LoopLocator(Kind.INVENTORY, slot, "");
    }

    public static LoopLocator curio(String identifier, int slot) {
        return new LoopLocator(Kind.CURIO, slot, identifier);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeVarInt(index);
        buf.writeUtf(curioId);
    }

    public static LoopLocator decode(RegistryFriendlyByteBuf buf) {
        return new LoopLocator(buf.readEnum(Kind.class), buf.readVarInt(), buf.readUtf());
    }

    public ItemStack resolve(Player player) {
        return switch (kind) {
            case HAND_MAIN -> player.getItemInHand(InteractionHand.MAIN_HAND);
            case HAND_OFF -> player.getItemInHand(InteractionHand.OFF_HAND);
            case INVENTORY -> player.getInventory().getItem(index);
            case CURIO -> CuriosApi.getCuriosInventory(player)
                    .flatMap(handler -> handler.getStacksHandler(curioId))
                    .map(stacks -> stacks.getStacks().getStackInSlot(index))
                    .orElse(ItemStack.EMPTY);
        };
    }

    /** Re-seat the stack so Curios notices a curio-slot loop changed and re-syncs it. No-op elsewhere. */
    public void afterChange(Player player, ItemStack loop) {
        if (kind != Kind.CURIO) return;
        CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler(curioId))
                .ifPresent(stacks -> stacks.getStacks().setStackInSlot(index, loop));
    }

    /** Where this exact stack currently sits on the player, or null if it isn't on the player. */
    public static LoopLocator find(Player player, ItemStack target) {
        if (player.getMainHandItem() == target) return hand(InteractionHand.MAIN_HAND);
        if (player.getOffhandItem() == target) return hand(InteractionHand.OFF_HAND);

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == target) return inventory(i);
        }

        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(stack -> stack == target))
                .map(result -> curio(result.slotContext().identifier(), result.slotContext().index()))
                .orElse(null);
    }
}

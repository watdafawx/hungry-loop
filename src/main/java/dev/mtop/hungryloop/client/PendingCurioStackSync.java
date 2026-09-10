package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.HungryLoop;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.common.network.client.CuriosClientPackets;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Holds back a per-slot curio stack sync that arrived before the client had that slot, and replays
 * it once the slot count catches up.
 *
 * <p>The loop grows a curio slot type and fills the new slot in the same server tick, but Curios
 * sends the stack ({@code SPacketSyncStack}) before the slot-count update. Applying it early throws
 * {@code Slot N not in valid range} and disconnects the player, so we used to drop it - and then the
 * mirror simply never arrived, because the server has nothing more to send for a slot it considers
 * settled. That left the client one mirror short, which is what surfaced as a ghost item in the
 * Curios screen. Deferring instead means nothing is lost.
 */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class PendingCurioStackSync {

    private static final int EXPIRY_TICKS = 100;

    private static final class Entry {
        final SPacketSyncStack packet;
        int ticksLeft = EXPIRY_TICKS;

        Entry(SPacketSyncStack packet) {
            this.packet = packet;
        }

        boolean sameSlot(SPacketSyncStack other) {
            return packet.entityId() == other.entityId()
                    && packet.slotId() == other.slotId()
                    && packet.handlerType() == other.handlerType()
                    && packet.curioId().equals(other.curioId());
        }
    }

    private static final List<Entry> PENDING = new ArrayList<>();
    private static boolean replaying;

    private PendingCurioStackSync() {}

    /** True while we're re-dispatching a held packet, so the intercept doesn't re-queue it. */
    public static boolean isReplaying() {
        return replaying;
    }

    public static void defer(SPacketSyncStack packet) {
        if (replaying) return;
        PENDING.removeIf(e -> e.sameSlot(packet));
        PENDING.add(new Entry(packet));
    }

    /** Whether the client's handler for this packet's type has the slot yet. */
    public static boolean slotExists(SPacketSyncStack packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.entityId()) instanceof LivingEntity entity)) {
            return true; // not our problem - let Curios handle it
        }
        int slots = CuriosApi.getCuriosInventory(entity)
                .flatMap(handler -> handler.getStacksHandler(packet.curioId()))
                .map(sh -> Math.max(sh.getStacks().getSlots(), sh.getCosmeticStacks().getSlots()))
                .orElse(0);
        return packet.slotId() < slots;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (PENDING.isEmpty()) return;

        replaying = true;
        try {
            for (Iterator<Entry> it = PENDING.iterator(); it.hasNext(); ) {
                Entry entry = it.next();
                if (slotExists(entry.packet)) {
                    CuriosClientPackets.handle(entry.packet);
                    it.remove();
                } else if (--entry.ticksLeft <= 0) {
                    it.remove();
                }
            }
        } finally {
            replaying = false;
        }
    }

    /** Client left the world - nothing queued is meaningful any more. */
    public static void clear() {
        PENDING.clear();
    }
}

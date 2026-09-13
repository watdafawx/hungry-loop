package dev.mtop.hungryloop.curio;

import net.minecraft.core.NonNullList;

import java.util.List;

/** Implemented on Curios' {@code CurioSlot} by {@code CurioSlotMixin}; see {@link CuriosRebuildGate}. */
public interface CurioSlotRebind {

    /** Point the slot at its handler's current render / active-state lists. */
    void hungryloop$rebind(NonNullList<Boolean> renders, List<Boolean> actives);

    /** The render list this slot reads (identity-checked by the selftest). */
    NonNullList<Boolean> hungryloop$renders();
}

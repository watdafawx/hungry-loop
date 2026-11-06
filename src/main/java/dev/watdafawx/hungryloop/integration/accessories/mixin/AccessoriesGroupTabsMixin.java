package dev.watdafawx.hungryloop.integration.accessories.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import io.wispforest.accessories.client.gui.AccessoriesScreen;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The original Accessories screen's group tabs jump to a row computed from each container's raw
 * size. {@link AccessoriesMenuLayoutMixin} removes the loop's mirror rows from the column, so count
 * a container without them too - otherwise every tab below a mirrored group lands rows too far down.
 */
@Mixin(value = AccessoriesScreen.class, remap = false)
public abstract class AccessoriesGroupTabsMixin {

    @WrapOperation(method = "getGroups",
            at = @At(value = "INVOKE", target = "Lio/wispforest/accessories/impl/ExpandedSimpleContainer;getContainerSize()I"))
    private int hungryloop$sizeWithoutMirrors(ExpandedSimpleContainer container, Operation<Integer> original) {
        int size = original.call(container);
        int mirrors = 0;
        for (int i = 0; i < size; i++) if (LoopSlotMirror.isMirror(container.getItem(i))) mirrors++;
        return size - mirrors;
    }
}

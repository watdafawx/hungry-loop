package dev.watdafawx.hungryloop.integration.accessories.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies {@code hungryloop.accessories.mixins.json} only when a supported Accessories is installed
 * ({@link dev.watdafawx.hungryloop.integration.accessories.AccessoriesSupport}). The config is
 * {@code required} with {@code defaultRequire 1} so a broken target fails loudly in packs that do have
 * Accessories - but without this gate those same targets would crash every pack that doesn't.
 * Must not reference any Accessories type itself: it loads before mods do.
 */
public final class AccessoriesMixinPlugin implements IMixinConfigPlugin {

    private static final boolean ACCESSORIES = dev.watdafawx.hungryloop.integration.accessories.AccessoriesSupport.ENABLED;

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ACCESSORIES;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

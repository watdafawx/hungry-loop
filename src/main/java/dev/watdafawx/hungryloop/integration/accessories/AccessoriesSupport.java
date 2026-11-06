package dev.watdafawx.hungryloop.integration.accessories;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Whether the Accessories integration is on. Accessories is optional and declared for any version
 * (so the loader never refuses to start over it); the integration only switches on for versions
 * whose APIs we've checked. On an older Accessories, or none, the mod runs on plain Curios.
 *
 * <p>Uses only the loading mod list, so it is safe to read from the mixin plugin, before mods load.
 * No Accessories types here.
 */
public final class AccessoriesSupport {

    /** Oldest Accessories whose APIs we use were checked against (1.1.0-beta.48 and .54). */
    private static final ArtifactVersion MIN = new DefaultArtifactVersion("1.1.0-beta.48");

    public static final boolean ENABLED = compute();

    private AccessoriesSupport() {}

    private static boolean compute() {
        ModFileInfo file = LoadingModList.get().getModFileById("accessories");
        if (file == null) return false;
        return file.getMods().stream()
                .filter(m -> m.getModId().equals("accessories"))
                .anyMatch(m -> m.getVersion().compareTo(MIN) >= 0);
    }
}

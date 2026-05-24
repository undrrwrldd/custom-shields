package dev.undrrwrldd.customshields.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RuntimePack {

    private static final Logger LOGGER = LoggerFactory.getLogger("customshields");
    private static final String PACK_FOLDER_NAME = "customshields-runtime";
    private static final RuntimePack INSTANCE = new RuntimePack();
    public static RuntimePack get() { return INSTANCE; }

    public Path packDir() {
        Path mcRoot = FabricLoader.getInstance().getGameDir();
        return mcRoot.resolve("resourcepacks").resolve(PACK_FOLDER_NAME);
    }

    public void ensureScaffold() {
        try {
            Path dir = packDir();
            Files.createDirectories(dir.resolve("assets/minecraft/textures/entity"));
            File mcmeta = dir.resolve("pack.mcmeta").toFile();
            // ALWAYS write the pack.mcmeta file to ensure it has the correct pack_format for the current MC version
            try (FileWriter w = new FileWriter(mcmeta)) {
                w.write("{\n  \"pack\": {\n    \"pack_format\": 34,\n    \"description\": \"Custom Shields runtime pack (auto-generated)\"\n  }\n}\n");
            }
            LOGGER.info("[CustomShields] Runtime pack scaffold ready at {}", dir);
        } catch (Exception e) {
            LOGGER.error("[CustomShields] Failed to ensure runtime pack scaffold", e);
        }
    }

    public void writeShieldImage(BufferedImage img) {
        try {
            ensureScaffold();
            Path basePath = packDir().resolve("assets/minecraft/textures/entity/shield_base.png");
            Path noPatternPath = packDir().resolve("assets/minecraft/textures/entity/shield_base_nopattern.png");
            ImageIO.write(img, "png", basePath.toFile());
            ImageIO.write(img, "png", noPatternPath.toFile());
            LOGGER.info("[CustomShields] Wrote shield textures to runtime pack ({}x{})", img.getWidth(), img.getHeight());
        } catch (Exception e) {
            LOGGER.error("[CustomShields] Failed to write shield image to runtime pack", e);
        }
    }

    public void enableAndReload() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                ResourcePackManager mgr = client.getResourcePackManager();
                mgr.scanPacks();

                String resolved = "file/" + PACK_FOLDER_NAME;
                if (!mgr.hasProfile(resolved)) resolved = "dir/" + PACK_FOLDER_NAME;
                if (!mgr.hasProfile(resolved)) resolved = PACK_FOLDER_NAME;
                final String packId = resolved;

                // Step 1: disable our pack and reload to force atlas eviction
                Set<String> withoutOurs = new LinkedHashSet<>(mgr.getEnabledIds());
                withoutOurs.remove(packId);
                mgr.setEnabledProfiles(new ArrayList<>(withoutOurs));
                LOGGER.info("[CustomShields] Toggle off; reloading without our pack: {}", withoutOurs);

                client.reloadResources().whenComplete((v1, e1) -> {
                    if (e1 != null) {
                        LOGGER.error("[CustomShields] First reload (toggle off) failed", e1);
                        return;
                    }
                    // Step 2: re-enable our pack at top priority and reload again
                    client.execute(() -> {
                        try {
                            mgr.scanPacks();
                            Set<String> withOurs = new LinkedHashSet<>(mgr.getEnabledIds());
                            withOurs.remove(packId);
                            withOurs.add(packId);
                            List<String> ordered = new ArrayList<>(withOurs);
                            mgr.setEnabledProfiles(ordered);
                            LOGGER.info("[CustomShields] Toggle on; reloading with our pack: {}", ordered);
                            client.reloadResources().whenComplete((v2, e2) -> {
                                if (e2 != null) {
                                    LOGGER.error("[CustomShields] Second reload (toggle on) failed", e2);
                                } else {
                                    LOGGER.info("[CustomShields] reloadResources() completed; shield should be updated");
                                }
                            });
                        } catch (Exception inner) {
                            LOGGER.error("[CustomShields] Failed during toggle-on phase", inner);
                        }
                    });
                });
            } catch (Exception e) {
                LOGGER.error("[CustomShields] Failed to enable/reload runtime pack", e);
            }
        });
    }
}

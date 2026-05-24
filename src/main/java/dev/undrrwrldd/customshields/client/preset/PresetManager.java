package dev.undrrwrldd.customshields.client.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.undrrwrldd.customshields.client.ImageProcessor;
import dev.undrrwrldd.customshields.client.RuntimePack;
import dev.undrrwrldd.customshields.client.ShieldTextureManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PresetManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("customshields");
    private static final PresetManager INSTANCE = new PresetManager();
    public static PresetManager get() { return INSTANCE; }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, ShieldPreset> presets = new LinkedHashMap<>();
    private String activeName = "";

    public File configDir() {
        File dir = FabricLoader.getInstance().getConfigDir().resolve("customshields").toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.error("[CustomShields] Failed to create config directory at {}", dir.getAbsolutePath());
        }
        return dir;
    }

    public File configFile() {
        return new File(configDir(), "presets.json");
    }

    public void load() {
        File f = configFile();
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            Type t = new TypeToken<SavedState>() {}.getType();
            SavedState s = gson.fromJson(r, t);
            if (s != null) {
                presets.clear();
                if (s.presets != null) {
                    for (ShieldPreset p : s.presets) presets.put(p.name, p);
                }
                activeName = s.active == null ? "" : s.active;
            }
        } catch (Exception e) {
            LOGGER.error("[CustomShields] Failed to load presets from {}", f.getAbsolutePath(), e);
        }
    }

    public boolean save() {
        SavedState s = new SavedState();
        s.presets = new ArrayList<>(presets.values());
        s.active = activeName;
        File file = configFile();
        try (FileWriter w = new FileWriter(file)) {
            gson.toJson(s, w);
        } catch (Exception e) {
            LOGGER.error("[CustomShields] Failed to save presets to {}", file.getAbsolutePath(), e);
            return false;
        }
        if (!file.exists() || file.length() == 0) {
            LOGGER.error("[CustomShields] Preset file did not persist to {}", file.getAbsolutePath());
            return false;
        }
        return true;
    }

    public List<ShieldPreset> list() { return new ArrayList<>(presets.values()); }

    public ShieldPreset get(String name) { return presets.get(name); }

    public boolean put(ShieldPreset p) {
        presets.put(p.name, p);
        return save();
    }

    public boolean remove(String name) {
        presets.remove(name);
        if (activeName.equals(name)) activeName = "";
        return save();
    }

    public String getActiveName() { return activeName; }

    public boolean setActive(String name) {
        activeName = name == null ? "" : name;
        return save();
    }

    public void applyActive() {
        if (activeName.isEmpty()) return;
        ShieldPreset p = presets.get(activeName);
        if (p != null) apply(p);
    }

    public boolean apply(ShieldPreset p) {
        return applyWithResult(p).success;
    }

    public ApplyResult applyWithResult(ShieldPreset p) {
        try {
            LOGGER.info("[CustomShields] apply() start: name='{}' path='{}'", p.name, p.imagePath);
            File img = new File(p.imagePath);
            if (!img.exists()) {
                LOGGER.warn("[CustomShields] apply() aborted: image does not exist at {}", p.imagePath);
                return ApplyResult.fail("image file not found");
            }
            List<ImageProcessor.Frame> raw = ImageProcessor.readAll(img);
            if (raw.isEmpty()) {
                LOGGER.warn("[CustomShields] apply() aborted: image had no frames at {}", p.imagePath);
                return ApplyResult.fail("image had no decodable frames");
            }
            LOGGER.info("[CustomShields] apply() decoded {} frames from {}", raw.size(), p.imagePath);
            List<BufferedImage> runtimeFrames = new ArrayList<>();
            List<BufferedImage> previewFrames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            int limit = p.animated ? raw.size() : 1;
            for (int i = 0; i < limit; i++) {
                ImageProcessor.Frame f = raw.get(i);
                ImageProcessor.EdgeStyle style = ImageProcessor.EdgeStyle.fromName(p.edgeStyle);
                BufferedImage runtime = ImageProcessor.composeRuntime(f.image, p.scale, p.offsetX, p.offsetY, style);
                runtimeFrames.add(runtime);
                previewFrames.add(ImageProcessor.previewFromRuntime(runtime));
                delays.add(f.delayMs);
            }
            if (previewFrames.size() == 1) {
                ShieldTextureManager.get().setStaticFrame(previewFrames.get(0));
            } else {
                ShieldTextureManager.get().setAnimatedFrames(previewFrames, delays);
            }
            RuntimePack.get().writeShieldImage(runtimeFrames.get(0));
            RuntimePack.get().enableAndReload();
            LOGGER.info("[CustomShields] Applied preset '{}' ({} frames)", p.name, runtimeFrames.size());
            return ApplyResult.ok();
        } catch (Exception e) {
            LOGGER.error("[CustomShields] Failed to apply preset", e);
            return ApplyResult.fail("exception while applying (see log)");
        }
    }

    public static class ApplyResult {
        public final boolean success;
        public final String error;

        private ApplyResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public static ApplyResult ok() { return new ApplyResult(true, null); }
        public static ApplyResult fail(String error) { return new ApplyResult(false, error); }
    }

    public static class SavedState {
        public List<ShieldPreset> presets;
        public String active;
    }
}

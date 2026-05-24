package dev.undrrwrldd.customshields.client.gui;

import dev.undrrwrldd.customshields.client.CustomShieldsClient;
import dev.undrrwrldd.customshields.client.ImageProcessor;
import dev.undrrwrldd.customshields.client.preset.PresetManager;
import dev.undrrwrldd.customshields.client.preset.ShieldPreset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class PresetListWidget extends AlwaysSelectedEntryListWidget<PresetListWidget.Entry> {

    private static final Logger LOGGER = LoggerFactory.getLogger("customshields");
    private final CustomShieldScreen parent;

    public PresetListWidget(MinecraftClient client, int x, int y, int width, int height, int itemHeight, CustomShieldScreen parent) {
        super(client, width, height, y, itemHeight);
        this.setX(x);
        this.parent = parent;
    }

    public void refresh() {
        this.clearEntries();
        List<ShieldPreset> presets = PresetManager.get().list();
        if (presets.isEmpty()) {
            this.addEntry(new Entry(null));
            return;
        }
        for (ShieldPreset p : presets) {
            this.addEntry(new Entry(p));
        }
        selectByName(PresetManager.get().getActiveName());
    }

    public void selectByName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        for (Entry entry : this.children()) {
            if (name.equals(entry.preset.name)) {
                this.setSelected(entry);
                return;
            }
        }
    }

    public ShieldPreset getSelectedPreset() {
        Entry e = getSelectedOrNull();
        return e == null ? null : e.preset;
    }

    @Override
    public int getRowWidth() { return this.width - 10; }

    public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        public final ShieldPreset preset;
        private final Identifier previewId;

        public Entry(ShieldPreset preset) {
            this.preset = preset;
            this.previewId = preset == null ? null : registerPreview(preset);
        }

        @Override
        public Text getNarration() {
            return preset == null ? Text.literal("No presets saved") : Text.literal(displayName());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (preset == null) {
                int x = getContentX();
                int y = getContentY();
                int w = getRowWidth();
                ctx.fill(x, y, x + w, y + 22 - 1, 0x88202020);
                ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal("No presets saved"), x + w / 2, y + 7, 0xFFAAAAAA);
                return;
            }
            String active = PresetManager.get().getActiveName();
            boolean isActive = preset.name.equals(active);
            int x = getContentX();
            int y = getContentY();
            int w = getRowWidth();
            int h = 22;
            ctx.fill(x, y, x + w, y + h - 1, hovered ? 0xAA3A3A3A : 0x88202020);
            ctx.fill(x, y, x + w, y + 1, isActive ? 0xFF55FF55 : 0xFF505050);
            if (previewId != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, previewId, x + 3, y + 3, 0.0F, 0.0F, 16, 16,
                        16, 16, 16, 16);
            } else {
                ctx.fill(x + 3, y + 3, x + 19, y + 19, 0xFF404040);
            }
            int color = isActive ? 0xFF55FF55 : 0xFFFFFFFF;
            String label = (isActive ? "\u25B6 " : "") + displayName();
            ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(label), x + 24, y + 7, color);
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (preset == null) {
                return false;
            }
            PresetListWidget.this.setSelected(this);
            if (button == 0) {
                parent.loadFromPreset(preset);
            }
            return true;
        }

        private Identifier registerPreview(ShieldPreset preset) {
            try {
                File file = new File(preset.imagePath);
                if (!file.exists()) {
                    LOGGER.warn("[CustomShields] Preset '{}' image not found: {}", preset.name, preset.imagePath);
                    return null;
                }
                List<ImageProcessor.Frame> frames = ImageProcessor.readAll(file);
                if (frames.isEmpty()) {
                    LOGGER.warn("[CustomShields] Preset '{}' has no decodable frames in {}", preset.name, preset.imagePath);
                    return null;
                }
                ImageProcessor.EdgeStyle style = ImageProcessor.EdgeStyle.fromName(preset.edgeStyle);
                BufferedImage preview = ImageProcessor.composePreview(frames.get(0).image, preset.scale, preset.offsetX, preset.offsetY, style);
                BufferedImage thumb = thumbnail(preview, 16, 16);
                NativeImage nativeImage = toNative(thumb);
                Identifier id = Identifier.of(CustomShieldsClient.MOD_ID,
                        "preset_preview/" + sanitize(preset.name) + "_" + Integer.toHexString(preset.imagePath.hashCode()));
                MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                        new NativeImageBackedTexture(() -> "customshields preset preview", nativeImage));
                LOGGER.info("[CustomShields] Registered preview thumbnail for preset '{}' as {}", preset.name, id);
                return id;
            } catch (Exception e) {
                LOGGER.error("[CustomShields] Failed to register preview thumbnail for preset '" + preset.name + "'", e);
                return null;
            }
        }

        private NativeImage toNative(BufferedImage img) {
            NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    out.setColorArgb(x, y, img.getRGB(x, y));
                }
            }
            return out;
        }

        private BufferedImage thumbnail(BufferedImage src, int w, int h) {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            return out;
        }

        private String sanitize(String input) {
            return input.toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
        }

        private String displayName() {
            if (preset == null || preset.name == null) return "(unnamed preset)";
            String trimmed = preset.name.trim();
            return trimmed.isEmpty() ? "(unnamed preset)" : trimmed;
        }
    }
}

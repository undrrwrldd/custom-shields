package dev.undrrwrldd.customshields.client.gui;

import dev.undrrwrldd.customshields.client.ImageProcessor;
import dev.undrrwrldd.customshields.client.ShieldTextureManager;
import dev.undrrwrldd.customshields.client.preset.PresetManager;
import dev.undrrwrldd.customshields.client.preset.ShieldPreset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomShieldScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("customshields");
    private static final Identifier PREVIEW_TEX = ShieldTextureManager.CUSTOM_TEXTURE_ID;

    private File pickedFile;
    private List<ImageProcessor.Frame> rawFrames = new ArrayList<>();
    private double scale = 1.0;
    private int offsetX = 0;
    private int offsetY = 0;
    private boolean animated = true;
    private ImageProcessor.EdgeStyle edgeStyle = ImageProcessor.EdgeStyle.SILVER_THIN;

    private TextFieldWidget nameField;
    private PresetListWidget presetList;
    private ButtonWidget applyBtn;
    private ButtonWidget animBtn;
    private ButtonWidget edgeBtn;
    private Text statusText;
    private int statusColor = 0xFFFFFFFF;
    private long statusUntilMs = 0L;

    public CustomShieldScreen() {
        super(Text.translatable("customshields.gui.title"));
    }

    @Override
    protected void init() {
        int leftX = 20;
        int rightX = this.width - 220;
        int topY = 40;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("customshields.gui.pick"), b -> openFileChooser())
                .dimensions(leftX, topY, 180, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("customshields.gui.clear"), b -> {
            ShieldTextureManager.get().disable();
            PresetManager.get().setActive("");
            pickedFile = null;
            rawFrames.clear();
        }).dimensions(leftX, topY + 24, 180, 20).build());

        this.addDrawableChild(new SliderWidget(leftX, topY + 60, 180, 20,
                Text.literal("Scale: 1.00"), normalize(scale, 0.1, 5.0)) {
            @Override protected void updateMessage() { setMessage(Text.literal(String.format("Scale: %.2f", scale))); }
            @Override protected void applyValue() { scale = denormalize(this.value, 0.1, 5.0); preview(); }
        });

        this.addDrawableChild(new SliderWidget(leftX, topY + 84, 180, 20,
                Text.literal("Offset X: 0"), normalize(offsetX, -64, 64)) {
            @Override protected void updateMessage() { setMessage(Text.literal("Offset X: " + offsetX)); }
            @Override protected void applyValue() { offsetX = (int) denormalize(this.value, -64, 64); preview(); }
        });

        this.addDrawableChild(new SliderWidget(leftX, topY + 108, 180, 20,
                Text.literal("Offset Y: 0"), normalize(offsetY, -64, 64)) {
            @Override protected void updateMessage() { setMessage(Text.literal("Offset Y: " + offsetY)); }
            @Override protected void applyValue() { offsetY = (int) denormalize(this.value, -64, 64); preview(); }
        });

        animBtn = ButtonWidget.builder(animText(), b -> {
            animated = !animated;
            animBtn.setMessage(animText());
            preview();
        }).dimensions(leftX, topY + 132, 180, 20).build();
        this.addDrawableChild(animBtn);

        edgeBtn = ButtonWidget.builder(edgeText(), b -> {
            edgeStyle = edgeStyle.next();
            edgeBtn.setMessage(edgeText());
            preview();
        }).dimensions(leftX, topY + 156, 180, 20).build();
        this.addDrawableChild(edgeBtn);

        nameField = new TextFieldWidget(this.textRenderer, leftX, topY + 188, 180, 20,
                Text.translatable("customshields.gui.preset_name"));
        nameField.setPlaceholder(Text.translatable("customshields.gui.preset_name"));
        this.addDrawableChild(nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("customshields.gui.save_preset"), b -> savePreset())
                .dimensions(leftX, topY + 212, 180, 20).build());

        presetList = new PresetListWidget(this.client, rightX, topY, 200, 160, 22, this);
        this.addDrawableChild(presetList);
        presetList.refresh();

        applyBtn = ButtonWidget.builder(Text.translatable("customshields.gui.apply"), b -> {
            ShieldPreset sel = presetList.getSelectedPreset();
            if (sel != null) {
                PresetManager.ApplyResult result = PresetManager.get().applyWithResult(sel);
                if (result.success) {
                    PresetManager.get().setActive(sel.name);
                    setStatus(Text.literal("Preset applied"), 0xFF55FF55);
                } else {
                    setStatus(Text.literal("Apply failed: " + result.error), 0xFFFF5555);
                }
            }
        }).dimensions(rightX, topY + 168, 95, 20).build();
        this.addDrawableChild(applyBtn);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("customshields.gui.delete_preset"), b -> {
            ShieldPreset sel = presetList.getSelectedPreset();
            if (sel != null) {
                PresetManager.get().remove(sel.name);
                presetList.refresh();
            }
        }).dimensions(rightX + 105, topY + 168, 95, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("customshields.gui.close"), b -> this.close())
                .dimensions(this.width / 2 - 60, this.height - 30, 120, 20).build());
    }

    private Text animText() {
        return Text.literal("GIF Animation: " + (animated ? "ON" : "OFF"));
    }

    private Text edgeText() {
        return Text.literal("Edge Style: " + edgeStyle.label);
    }

    private double normalize(double v, double min, double max) { return (v - min) / (max - min); }
    private double denormalize(double v, double min, double max) { return min + v * (max - min); }

    private void setStatus(Text text, int color) {
        this.statusText = text;
        this.statusColor = color;
        this.statusUntilMs = System.currentTimeMillis() + 4000L;
    }

    private void openFileChooser() {
        new Thread(() -> {
            try {
                String result;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer filters = stack.mallocPointer(6);
                    filters.put(stack.UTF8("*.png"));
                    filters.put(stack.UTF8("*.jpg"));
                    filters.put(stack.UTF8("*.jpeg"));
                    filters.put(stack.UTF8("*.gif"));
                    filters.put(stack.UTF8("*.bmp"));
                    filters.put(stack.UTF8("*.webp"));
                    filters.flip();
                    result = TinyFileDialogs.tinyfd_openFileDialog(
                            "Select shield image",
                            pickedFile == null ? null : pickedFile.getAbsolutePath(),
                            filters,
                            "Images (*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.webp)",
                            false
                    );
                }
                if (result != null && !result.isBlank()) {
                    File f = new File(result);
                    MinecraftClient.getInstance().execute(() -> loadFile(f));
                }
            } catch (Exception ignored) {}
        }, "CustomShields-FileChooser").start();
    }

    private void loadFile(File f) {
        try {
            pickedFile = f;
            rawFrames = ImageProcessor.readAll(f);
            preview();
        } catch (Exception e) {
            rawFrames.clear();
            pickedFile = null;
        }
    }

    private void preview() {
        if (rawFrames.isEmpty()) return;
        try {
            List<BufferedImage> processed = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            int limit = animated ? rawFrames.size() : 1;
            for (int i = 0; i < limit; i++) {
                ImageProcessor.Frame fr = rawFrames.get(i);
                processed.add(ImageProcessor.composePreview(fr.image, scale, offsetX, offsetY, edgeStyle));
                delays.add(fr.delayMs);
            }
            if (processed.size() == 1) {
                ShieldTextureManager.get().setStaticFrame(processed.get(0));
            } else {
                ShieldTextureManager.get().setAnimatedFrames(processed, delays);
            }
        } catch (Exception ignored) {}
    }

    private void savePreset() {
        if (pickedFile == null) {
            LOGGER.warn("[CustomShields] savePreset called but no file picked");
            setStatus(Text.literal("Pick an image first"), 0xFFFF5555);
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "preset_" + System.currentTimeMillis();
        String baseName = name;
        int suffix = 2;
        while (PresetManager.get().get(name) != null) {
            name = baseName + " (" + suffix + ")";
            suffix++;
        }
        if (!name.equals(baseName)) {
            nameField.setText(name);
        }
        String absPath = pickedFile.getAbsolutePath();
        LOGGER.info("[CustomShields] Saving new preset name='{}' path='{}' scale={} offsetX={} offsetY={} animated={} edgeStyle={}",
                name, absPath, scale, offsetX, offsetY, animated, edgeStyle.name());
        ShieldPreset p = new ShieldPreset(name, absPath, scale, offsetX, offsetY, animated, edgeStyle.name());
        boolean saved = PresetManager.get().put(p);
        boolean activeSaved = PresetManager.get().setActive(name);
        presetList.refresh();
        PresetManager.ApplyResult result = PresetManager.get().applyWithResult(p);
        LOGGER.info("[CustomShields] savePreset auto-apply result: {}", result.success);
        if (!saved || !activeSaved) {
            setStatus(Text.literal("Failed to save presets.json (see log)"), 0xFFFF5555);
        } else if (!result.success) {
            setStatus(Text.literal("Preset saved, apply failed: " + result.error), 0xFFFFAA00);
        } else {
            setStatus(Text.literal("Preset saved"), 0xFF55FF55);
        }
    }

    public void loadFromPreset(ShieldPreset p) {
        try {
            this.pickedFile = new File(p.imagePath);
            if (!pickedFile.exists()) return;
            this.rawFrames = ImageProcessor.readAll(pickedFile);
            this.scale = p.scale;
            this.offsetX = p.offsetX;
            this.offsetY = p.offsetY;
            this.animated = p.animated;
            this.edgeStyle = ImageProcessor.EdgeStyle.fromName(p.edgeStyle);
            this.nameField.setText(p.name);
            if (animBtn != null) animBtn.setMessage(animText());
            if (edgeBtn != null) edgeBtn.setMessage(edgeText());
            this.clearAndInit();
            preview();
        } catch (Exception ignored) {}
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Developed by @undrrwrldd").formatted(net.minecraft.util.Formatting.GRAY),
                this.width / 2, 24, 0xAAAAAA);

        int previewW = 96;
        int previewH = 176;
        int px = 20 + 200;
        int py = 50;
        ctx.fill(px - 2, py - 2, px + previewW + 2, py + previewH + 2, 0xFF202020);
        if (ShieldTextureManager.get().isEnabled()) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PREVIEW_TEX,
                    px, py,
                    0.0F, 0.0F,
                    previewW, previewH,
                    previewW, previewH);
        } else {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("(no image)"),
                    px + previewW / 2, py + previewH / 2 - 4, 0x808080);
        }
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Live Preview"), px, py - 12, 0xFFFFFF);
        if (statusText != null) {
            if (System.currentTimeMillis() > statusUntilMs) {
                statusText = null;
            } else {
                ctx.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, this.height - 50, statusColor);
            }
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}

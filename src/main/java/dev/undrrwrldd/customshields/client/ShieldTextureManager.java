package dev.undrrwrldd.customshields.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class ShieldTextureManager {

    public static final Identifier CUSTOM_TEXTURE_ID = Identifier.of("customshields", "shield/custom");

    private static final ShieldTextureManager INSTANCE = new ShieldTextureManager();
    public static ShieldTextureManager get() { return INSTANCE; }

    private NativeImageBackedTexture texture;
    private final List<NativeImage> frames = new ArrayList<>();
    private final List<Integer> frameDelays = new ArrayList<>();
    private int currentFrame = 0;
    private long lastSwap = 0L;
    private boolean enabled = false;

    public void init(MinecraftClient client) {
        if (texture == null) {
            NativeImage blank = new NativeImage(ImageProcessor.PREVIEW_W, ImageProcessor.PREVIEW_H, false);
            texture = new NativeImageBackedTexture(() -> "Custom Shields", blank);
            client.getTextureManager().registerTexture(CUSTOM_TEXTURE_ID, texture);
        }
    }

    public boolean isEnabled() { return enabled && !frames.isEmpty(); }

    public void disable() {
        enabled = false;
        clearFrames();
    }

    public void setStaticFrame(BufferedImage img) {
        clearFrames();
        frames.add(toNative(img));
        frameDelays.add(0);
        currentFrame = 0;
        enabled = true;
        upload();
    }

    public void setAnimatedFrames(List<BufferedImage> imgs, List<Integer> delays) {
        clearFrames();
        for (int i = 0; i < imgs.size(); i++) {
            frames.add(toNative(imgs.get(i)));
            frameDelays.add(i < delays.size() ? Math.max(20, delays.get(i)) : 100);
        }
        currentFrame = 0;
        lastSwap = System.currentTimeMillis();
        enabled = true;
        upload();
    }

    public void tick() {
        if (!enabled || frames.size() <= 1) return;
        long now = System.currentTimeMillis();
        int delay = frameDelays.get(currentFrame);
        if (delay <= 0) return;
        if (now - lastSwap >= delay) {
            currentFrame = (currentFrame + 1) % frames.size();
            lastSwap = now;
            upload();
        }
    }

    private void upload() {
        if (texture == null || frames.isEmpty()) return;
        NativeImage src = frames.get(currentFrame);
        NativeImage dst = texture.getImage();
        if (dst == null) return;
        for (int y = 0; y < dst.getHeight(); y++) {
            for (int x = 0; x < dst.getWidth(); x++) {
                int color = (x < src.getWidth() && y < src.getHeight()) ? src.getColorArgb(x, y) : 0;
                dst.setColorArgb(x, y, color);
            }
        }
        texture.upload();
    }

    private void clearFrames() {
        for (NativeImage img : frames) {
            try { img.close(); } catch (Exception ignored) {}
        }
        frames.clear();
        frameDelays.clear();
    }

    private static NativeImage toNative(BufferedImage img) {
        NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                out.setColorArgb(x, y, argb);
            }
        }
        return out;
    }
}

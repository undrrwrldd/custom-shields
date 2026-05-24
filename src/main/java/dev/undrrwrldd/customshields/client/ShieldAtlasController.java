package dev.undrrwrldd.customshields.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteHolder;
import net.minecraft.client.render.TexturedRenderLayers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class ShieldAtlasController {

    private static final Logger LOGGER = LoggerFactory.getLogger("customshields");
    private static final ShieldAtlasController INSTANCE = new ShieldAtlasController();
    public static ShieldAtlasController get() { return INSTANCE; }

    private Sprite sprite;
    private NativeImage originalCopy;
    private int spriteW = 0;
    private int spriteH = 0;
    private boolean initialized = false;

    public void captureFrom(SpriteHolder holder) {
        try {
            Sprite s = holder.getSprite(TexturedRenderLayers.SHIELD_BASE);
            if (s == null) {
                LOGGER.warn("[CustomShields] SpriteHolder returned null for SHIELD_BASE");
                return;
            }
            SpriteContents contents = s.getContents();
            NativeImage live = contents.image;
            this.sprite = s;
            this.spriteW = live.getWidth();
            this.spriteH = live.getHeight();
            if (this.originalCopy == null) {
                NativeImage backup = new NativeImage(spriteW, spriteH, false);
                for (int y = 0; y < spriteH; y++) {
                    for (int x = 0; x < spriteW; x++) {
                        backup.setColorArgb(x, y, live.getColorArgb(x, y));
                    }
                }
                this.originalCopy = backup;
                ImageProcessor.setEdgeBaseTexture(toBufferedImage(backup));
            }
            this.initialized = true;
            LOGGER.info("[CustomShields] Captured shield sprite {}x{} on atlas {} at ({},{})",
                    spriteW, spriteH, s.getAtlasId(), s.getX(), s.getY());
        } catch (Throwable t) {
            LOGGER.error("[CustomShields] Failed to capture shield sprite", t);
        }
    }

    public boolean isReady() { return initialized && sprite != null; }

    public int getSpriteWidth() { return spriteW; }
    public int getSpriteHeight() { return spriteH; }

    public void applyBufferedImage(BufferedImage src) {
        if (!isReady()) {
            LOGGER.warn("[CustomShields] applyBufferedImage called but controller not ready");
            return;
        }
        try {
            BufferedImage scaled = resize(src, spriteW, spriteH);
            NativeImage live = sprite.getContents().image;
            for (int y = 0; y < spriteH; y++) {
                for (int x = 0; x < spriteW; x++) {
                    int argb = scaled.getRGB(x, y);
                    live.setColorArgb(x, y, argb);
                }
            }
            uploadSprite();
            LOGGER.info("[CustomShields] Applied custom image {}x{} to shield atlas slot", spriteW, spriteH);
        } catch (Throwable t) {
            LOGGER.error("[CustomShields] Failed to apply image to atlas", t);
        }
    }

    public void restore() {
        if (!isReady() || originalCopy == null) return;
        try {
            NativeImage live = sprite.getContents().image;
            for (int y = 0; y < spriteH; y++) {
                for (int x = 0; x < spriteW; x++) {
                    live.setColorArgb(x, y, originalCopy.getColorArgb(x, y));
                }
            }
            uploadSprite();
            LOGGER.info("[CustomShields] Restored original shield texture");
        } catch (Throwable t) {
            LOGGER.error("[CustomShields] Failed to restore atlas", t);
        }
    }

    private void uploadSprite() {
        Runnable r = () -> {
            try {
                AbstractTexture atlas = MinecraftClient.getInstance().getTextureManager().getTexture(sprite.getAtlasId());
                if (atlas == null) {
                    LOGGER.warn("[CustomShields] No AbstractTexture for atlas id {}", sprite.getAtlasId());
                    return;
                }
                sprite.upload(atlas.getGlTextureView().texture(), 0);
            } catch (Throwable t) {
                LOGGER.error("[CustomShields] Sprite upload failed", t);
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            r.run();
        } else {
            MinecraftClient.getInstance().execute(r);
        }
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toBufferedImage(NativeImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                out.setRGB(x, y, src.getColorArgb(x, y));
            }
        }
        return out;
    }
}

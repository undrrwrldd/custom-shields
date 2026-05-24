package dev.undrrwrldd.customshields.client;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ImageProcessor {

    public static final int PREVIEW_W = 96;
    public static final int PREVIEW_H = 176;
    public static final int RUNTIME_W = 512;
    public static final int RUNTIME_H = 512;

    public enum EdgeOverlayMode { NONE, RIM, FACE }

    public enum EdgeStyle {
        NONE("None", 0, 0x00000000, EdgeOverlayMode.NONE, 0.0f),
        SILVER_THIN("Silver (1px)", 8, 0xFFD0D5D8, EdgeOverlayMode.RIM, 1.0f),
        SILVER_THICK("Silver (2px)", 16, 0xFFB8BEC2, EdgeOverlayMode.RIM, 1.0f),
        SHIELD_TEXTURE("Silver Outline", 8, 0xFFC8CCD0, EdgeOverlayMode.RIM, 1.0f);

        public final String label;
        public final int thickness;
        public final int color;
        public final EdgeOverlayMode mode;
        public final float alpha;

        EdgeStyle(String label, int thickness, int color, EdgeOverlayMode mode, float alpha) {
            this.label = label;
            this.thickness = thickness;
            this.color = color;
            this.mode = mode;
            this.alpha = alpha;
        }

        public EdgeStyle next() {
            EdgeStyle[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public static EdgeStyle fromName(String name) {
            if (name == null) return NONE;
            for (EdgeStyle style : values()) {
                if (style.name().equalsIgnoreCase(name)) return style;
            }
            return NONE;
        }
    }

    private static BufferedImage edgeBaseTexture;
    private static final Map<String, BufferedImage> edgeOverlayCache = new HashMap<>();

    public static void setEdgeBaseTexture(BufferedImage base) {
        edgeBaseTexture = base;
        edgeOverlayCache.clear();
    }

    private static final int PREVIEW_FACE_W = 96;
    private static final int PREVIEW_FACE_H = 176;
    private static final int PREVIEW_FACE_X = 0;
    private static final int PREVIEW_FACE_Y = 0;

    private static final int RUNTIME_FACE_W = 96;
    private static final int RUNTIME_FACE_H = 176;
    private static final int RUNTIME_FACE_X = 8;
    private static final int RUNTIME_FACE_Y = 8;
    private static final int RUNTIME_BACK_X = 112;
    private static final int RUNTIME_BACK_Y = 8;

    public static final class Frame {
        public final BufferedImage image;
        public final int delayMs;
        public Frame(BufferedImage img, int delay) { this.image = img; this.delayMs = delay; }
    }

    public static List<Frame> readAll(File file) throws Exception {
        List<Frame> frames = new ArrayList<>();
        String name = file.getName().toLowerCase();
        if (name.endsWith(".gif")) {
            try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext()) throw new IllegalStateException("No GIF reader available");
                ImageReader reader = readers.next();
                reader.setInput(stream);
                int count = reader.getNumImages(true);
                BufferedImage canvas = null;
                for (int i = 0; i < count; i++) {
                    BufferedImage frame = reader.read(i);
                    if (canvas == null) {
                        canvas = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    }
                    Graphics2D g = canvas.createGraphics();
                    g.drawImage(frame, 0, 0, null);
                    g.dispose();
                    BufferedImage copy = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    copy.getGraphics().drawImage(canvas, 0, 0, null);
                    int delay = 100;
                    try {
                        javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(i);
                        org.w3c.dom.Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                        for (int n = 0; n < root.getChildNodes().getLength(); n++) {
                            org.w3c.dom.Node node = root.getChildNodes().item(n);
                            if ("GraphicControlExtension".equals(node.getNodeName())) {
                                String d = node.getAttributes().getNamedItem("delayTime").getNodeValue();
                                delay = Math.max(20, Integer.parseInt(d) * 10);
                            }
                        }
                    } catch (Exception ignored) {}
                    frames.add(new Frame(copy, delay));
                }
                reader.dispose();
            }
        } else {
            BufferedImage img = ImageIO.read(file);
            if (img == null) throw new IllegalStateException("Unsupported image format");
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(img, 0, 0, null);
            frames.add(new Frame(argb, 0));
        }
        return frames;
    }

    public static BufferedImage composeRuntime(BufferedImage source, double scale, int offsetX, int offsetY, EdgeStyle edgeStyle) {
        return compose(source, scale, offsetX, offsetY,
                RUNTIME_W, RUNTIME_H,
                RUNTIME_FACE_W, RUNTIME_FACE_H, RUNTIME_FACE_X, RUNTIME_FACE_Y,
                true, RUNTIME_BACK_X, RUNTIME_BACK_Y, edgeStyle);
    }

    public static BufferedImage composePreview(BufferedImage source, double scale, int offsetX, int offsetY, EdgeStyle edgeStyle) {
        BufferedImage runtime = composeRuntime(source, scale, offsetX, offsetY, edgeStyle);
        return cropFront(runtime);
    }

    private static BufferedImage compose(BufferedImage source, double scale, int offsetX, int offsetY,
                                         int canvasW, int canvasH,
                                         int faceW, int faceH, int faceX, int faceY,
                                         boolean drawBack, int backX, int backY, EdgeStyle edgeStyle) {
        BufferedImage out = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        BufferedImage base = getBaseTextureScaled(canvasW, canvasH);
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, canvasW, canvasH);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        if (base != null) {
            g.drawImage(base, 0, 0, null);
        } else {
            paintProceduralShieldBase(g, canvasW, canvasH);
        }

        int rimInset = edgeStyle == null ? 0 : Math.max(0, edgeStyle.thickness);
        // Cap the inset so the inner area can never collapse to zero or negative.
        int maxInset = Math.max(0, Math.min(faceW, faceH) / 2 - 1);
        if (rimInset > maxInset) rimInset = maxInset;

        int innerW = Math.max(1, faceW - rimInset * 2);
        int innerH = Math.max(1, faceH - rimInset * 2);
        int frontInnerX = faceX + rimInset;
        int frontInnerY = faceY + rimInset;
        int backInnerX = backX + rimInset;
        int backInnerY = backY + rimInset;


        // Stretch the image to fill the (possibly inset) shield face
        int drawW = (int) Math.round(innerW * scale);
        int drawH = (int) Math.round(innerH * scale);
        if (drawW < 1) drawW = 1;
        if (drawH < 1) drawH = 1;

        int dx = (innerW - drawW) / 2 + offsetX;
        int dy = (innerH - drawH) / 2 + offsetY;

        Shape oldClip = g.getClip();
        // Front: user image inside the inset rim. The base texture already drawn underneath
        // shows through the rim border (= wood for SHIELD_TEXTURE).
        g.setClip(frontInnerX, frontInnerY, innerW, innerH);
        drawScaled(g, source, frontInnerX + dx, frontInnerY + dy, drawW, drawH, false);
        if (drawBack) {
            g.setClip(backInnerX, backInnerY, innerW, innerH);
            drawScaled(g, source, backInnerX + dx, backInnerY + dy, drawW, drawH, true);
        }
        g.setClip(oldClip);

        // Paint a multi-tone silver rim strictly inside the face UV region (1 vanilla px = 8 canvas px).
        // The pattern is block-aligned to the vanilla pixel grid so each vanilla cell of the rim
        // gets a distinct silver tone after the atlas downsamples our 512x512 canvas.
        if (edgeStyle != null && edgeStyle.mode == EdgeOverlayMode.RIM && rimInset > 0) {
            paintSilverRim(g, faceX, faceY, faceW, faceH, rimInset);
            if (drawBack) {
                paintSilverRim(g, backX, backY, faceW, faceH, rimInset);
            }
        }

        g.dispose();
        return out;
    }

    private static void drawScaled(Graphics2D g, BufferedImage source, int x, int y, int w, int h, boolean flipX) {
        if (!flipX) {
            g.drawImage(source, x, y, w, h, null);
            return;
        }
        g.drawImage(source, x + w, y, x, y + h, 0, 0, source.getWidth(), source.getHeight(), null);
    }

    private static BufferedImage getBaseTextureScaled(int canvasW, int canvasH) {
        if (edgeBaseTexture == null) return null;
        String key = canvasW + "x" + canvasH;
        BufferedImage cached = edgeOverlayCache.get(key);
        if (cached != null) return cached;
        BufferedImage scaled = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(edgeBaseTexture, 0, 0, canvasW, canvasH, null);
        g.dispose();
        edgeOverlayCache.put(key, scaled);
        return scaled;
    }

    private static void drawOverlay(Graphics2D g, BufferedImage base, EdgeStyle style,
                                    int faceX, int faceY, int faceW, int faceH, int inset, boolean flip) {
        if (style == null || style.mode == EdgeOverlayMode.NONE) return;
        BufferedImage overlay = new BufferedImage(faceW, faceH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = overlay.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (base != null) {
            og.drawImage(base, 0, 0, faceW, faceH, faceX, faceY, faceX + faceW, faceY + faceH, null);
        } else {
            og.setColor(new Color(style.color, true));
            og.fillRect(0, 0, faceW, faceH);
        }
        og.dispose();

        if (style.mode == EdgeOverlayMode.RIM) {
            tintToSilver(overlay, style.color);
            Graphics2D clear = overlay.createGraphics();
            clear.setComposite(java.awt.AlphaComposite.Clear);
            int rim = Math.max(1, inset);
            clear.fillRect(rim, rim, faceW - rim * 2, faceH - rim * 2);
            clear.dispose();
        } else if (style.mode == EdgeOverlayMode.FACE) {
            removeStuds(overlay);
            applyAlpha(overlay, style.alpha);
        }

        if (flip) {
            drawScaled(g, overlay, faceX, faceY, faceW, faceH, true);
        } else {
            g.drawImage(overlay, faceX, faceY, null);
        }
    }

    private static void applyAlpha(BufferedImage img, float alpha) {
        float a = Math.max(0.0f, Math.min(1.0f, alpha));
        if (a >= 0.999f) return;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int ai = (argb >> 24) & 0xFF;
                if (ai == 0) continue;
                int na = Math.min(255, Math.round(ai * a));
                img.setRGB(x, y, (na << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    private static void removeStuds(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int band = Math.max(4, h / 4);
        boolean[][] visited = new boolean[h][w];
        int minSize = Math.max(6, (w * h) / 800);
        int maxSize = Math.max(minSize + 4, (w * h) / 80);
        for (int y = 0; y < band; y++) {
            for (int x = 0; x < w; x++) {
                if (visited[y][x]) continue;
                int argb = img.getRGB(x, y);
                if (!isStudCandidate(argb)) {
                    visited[y][x] = true;
                    continue;
                }
                int[] stackX = new int[w * band];
                int[] stackY = new int[w * band];
                int top = 0;
                stackX[top] = x;
                stackY[top] = y;
                top++;
                int count = 0;
                while (top > 0) {
                    top--;
                    int cx = stackX[top];
                    int cy = stackY[top];
                    if (cx < 0 || cy < 0 || cx >= w || cy >= band) continue;
                    if (visited[cy][cx]) continue;
                    visited[cy][cx] = true;
                    int c = img.getRGB(cx, cy);
                    if (!isStudCandidate(c)) continue;
                    stackX[top] = cx + 1; stackY[top] = cy; top++;
                    stackX[top] = cx - 1; stackY[top] = cy; top++;
                    stackX[top] = cx; stackY[top] = cy + 1; top++;
                    stackX[top] = cx; stackY[top] = cy - 1; top++;
                    count++;
                }
                if (count >= minSize && count <= maxSize) {
                    for (int yy = 0; yy < band; yy++) {
                        for (int xx = 0; xx < w; xx++) {
                            if (visited[yy][xx] && isStudCandidate(img.getRGB(xx, yy))) {
                                img.setRGB(xx, yy, 0x00000000);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isStudCandidate(int argb) {
        int a = (argb >> 24) & 0xFF;
        if (a < 200) return false;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int bright = (r + g + b) / 3;
        return bright > 190 && (max - min) < 20;
    }

    private static BufferedImage extractBaseFace(int faceW, int faceH) {
        if (edgeBaseTexture == null) return null;
        int baseW = edgeBaseTexture.getWidth();
        int baseH = edgeBaseTexture.getHeight();
        int fx = (int) Math.round(baseW * (2.0 / 64.0));
        int fy = 0;
        int fw = Math.max(1, (int) Math.round(baseW * (12.0 / 64.0)));
        int fh = Math.max(1, (int) Math.round(baseH * (22.0 / 64.0)));
        fx = Math.min(Math.max(0, fx), baseW - 1);
        fy = Math.min(Math.max(0, fy), baseH - 1);
        fw = Math.min(fw, baseW - fx);
        fh = Math.min(fh, baseH - fy);
        BufferedImage cropped = edgeBaseTexture.getSubimage(fx, fy, fw, fh);
        BufferedImage scaled = new BufferedImage(faceW, faceH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cropped, 0, 0, faceW, faceH, null);
        g.dispose();
        return scaled;
    }

    private static void tintToSilver(BufferedImage img, int target) {
        int tr = (target >> 16) & 0xFF;
        int tg = (target >> 8) & 0xFF;
        int tb = target & 0xFF;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int gray = (r + g + b) / 3;
                int nr = Math.min(255, (gray + tr) / 2 + 20);
                int ng = Math.min(255, (gray + tg) / 2 + 20);
                int nb = Math.min(255, (gray + tb) / 2 + 20);
                img.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
    }


    private static void paintSilverRim(Graphics2D g, int x, int y, int w, int h, int rim) {
        // Tones ordered from highlight to shadow.
        int[] tones = { 0xFFE8ECEF, 0xFFCBD0D4, 0xFFA6ACB1, 0xFF7C8288 };
        // Each tone runs for multiple vanilla pixels (1 vanilla px = 8 canvas px = `rim`).
        // A block of 4 vanilla pixels (= rim * 4) per tone gives slow, chunky variation.
        int block = Math.max(1, rim * 4);
        // Slow cycling order: lighter -> darker -> mid -> darker, etc. The simple sequence
        // {0,1,2,3,2,1} avoids harsh light/dark jumps and matches a hand-painted rim look.
        int[] seqH = { 0, 1, 2, 1, 0, 1, 2, 3 };
        int[] seqV = { 0, 1, 2, 3, 2, 1, 0, 1 };
        // Top strip
        for (int bx = 0; bx < w; bx += block) {
            int tone = tones[seqH[(bx / block) % seqH.length]];
            g.setColor(new Color(tone, true));
            g.fillRect(x + bx, y, Math.min(block, w - bx), rim);
        }
        // Bottom strip (offset so it looks different from top)
        for (int bx = 0; bx < w; bx += block) {
            int tone = tones[seqH[((bx / block) + 3) % seqH.length]];
            g.setColor(new Color(tone, true));
            g.fillRect(x + bx, y + h - rim, Math.min(block, w - bx), rim);
        }
        // Left strip
        for (int by = 0; by < h; by += block) {
            int tone = tones[seqV[(by / block) % seqV.length]];
            g.setColor(new Color(tone, true));
            g.fillRect(x, y + by, rim, Math.min(block, h - by));
        }
        // Right strip
        for (int by = 0; by < h; by += block) {
            int tone = tones[seqV[((by / block) + 2) % seqV.length]];
            g.setColor(new Color(tone, true));
            g.fillRect(x + w - rim, y + by, rim, Math.min(block, h - by));
        }
    }

    private static void paintWoodRect(Graphics2D g, int x, int y, int w, int h) {
        Color woodDark = new Color(0x5C3D2A);
        Color woodMid = new Color(0x6F4A33);
        Color woodLight = new Color(0x82593E);
        Color plankLine = new Color(0x3A2618);
        g.setColor(woodMid);
        g.fillRect(x, y, w, h);
        int plankWidth = Math.max(8, w / 8);
        for (int px = 0; px < w; px += plankWidth) {
            int variant = (px / plankWidth) % 3;
            Color c = variant == 0 ? woodDark : (variant == 1 ? woodMid : woodLight);
            g.setColor(c);
            g.fillRect(x + px, y, Math.min(plankWidth, w - px), h);
        }
        g.setColor(plankLine);
        for (int px = 0; px < w; px += plankWidth) {
            g.fillRect(x + px, y, 1, h);
        }
    }

    private static void paintProceduralShieldBase(Graphics2D g, int w, int h) {
        Color woodDark = new Color(0x5C3D2A);
        Color woodMid = new Color(0x6F4A33);
        Color woodLight = new Color(0x82593E);
        g.setColor(woodMid);
        g.fillRect(0, 0, w, h);
        // Subtle vertical plank grain
        int plankWidth = Math.max(8, w / 16);
        for (int x = 0; x < w; x += plankWidth) {
            int variant = (x / plankWidth) % 3;
            Color c = variant == 0 ? woodDark : (variant == 1 ? woodMid : woodLight);
            g.setColor(c);
            g.fillRect(x, 0, plankWidth, h);
        }
        // Horizontal subtle dark line every plankWidth*2 to suggest planks
        g.setColor(new Color(0x3A2618));
        for (int x = 0; x < w; x += plankWidth) {
            g.fillRect(x, 0, 1, h);
        }
    }

    private static BufferedImage cropFront(BufferedImage sheet) {
        BufferedImage out = new BufferedImage(PREVIEW_W, PREVIEW_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(sheet,
                0, 0, PREVIEW_W, PREVIEW_H,
                RUNTIME_FACE_X, RUNTIME_FACE_Y, RUNTIME_FACE_X + RUNTIME_FACE_W, RUNTIME_FACE_Y + RUNTIME_FACE_H,
                null);
        g.dispose();
        return out;
    }

    public static BufferedImage previewFromRuntime(BufferedImage runtimeSheet) {
        return cropFront(runtimeSheet);
    }

    private static void paintEdges(Graphics2D g, BufferedImage base, EdgeStyle style,
                                   int faceX, int faceY, int faceW, int faceH) {
        int edge = Math.max(1, Math.min(faceX, faceY));
        int leftX = faceX - edge;
        int rightX = faceX + faceW;
        int topY = faceY - edge;
        if (style != null && style.mode == EdgeOverlayMode.RIM) {
            if (base != null) {
                drawTintedEdge(g, base, style, faceX, topY, faceW, edge);   // Top
                drawTintedEdge(g, base, style, rightX, topY, faceW, edge);  // Bottom
                drawTintedEdge(g, base, style, leftX, faceY, edge, faceH);  // Left edge
                drawTintedEdge(g, base, style, rightX, faceY, edge, faceH); // Right edge
            } else {
                paintEdgeColor(g, new Color(style.color, true), faceX, topY, faceW, edge, leftX, faceY, edge, faceH, rightX);
            }
            return;
        }
        if (base == null) {
            paintEdgeColor(g, new Color(0xFF222222, true), faceX, topY, faceW, edge, leftX, faceY, edge, faceH, rightX);
        }
    }

    private static void paintEdgeColor(Graphics2D g, Color color,
                                       int topX, int topY, int topW, int topH,
                                       int leftX, int leftY, int leftW, int leftH,
                                       int rightX) {
        g.setColor(color);
        g.fillRect(topX, topY, topW, topH);     // Top
        g.fillRect(rightX, topY, topW, topH);   // Bottom
        g.fillRect(leftX, leftY, leftW, leftH); // Left edge
        g.fillRect(rightX, leftY, leftW, leftH);// Right edge
    }

    private static void drawTintedEdge(Graphics2D g, BufferedImage base, EdgeStyle style,
                                       int x, int y, int w, int h) {
        BufferedImage edge = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D eg = edge.createGraphics();
        eg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        eg.drawImage(base, 0, 0, w, h, x, y, x + w, y + h, null);
        eg.dispose();
        tintToSilver(edge, style.color);
        g.drawImage(edge, x, y, null);
    }

}

package dev.undrrwrldd.customshields.client.preset;

public class ShieldPreset {
    public String name = "preset";
    public String imagePath = "";
    public double scale = 1.0;
    public int offsetX = 0;
    public int offsetY = 0;
    public boolean animated = true;
    public String edgeStyle = "NONE";

    public ShieldPreset() {}

    public ShieldPreset(String name, String path, double scale, int ox, int oy, boolean animated, String edgeStyle) {
        this.name = name;
        this.imagePath = path;
        this.scale = scale;
        this.offsetX = ox;
        this.offsetY = oy;
        this.animated = animated;
        this.edgeStyle = edgeStyle;
    }
}

# Custom Shields

A client-side **Fabric** mod for **Minecraft 1.21.11** that lets you replace your shield texture with **any image** (PNG / JPG / JPEG / GIF / BMP / WEBP). Includes a full GUI to crop, scale and offset the image, plus saveable presets.

> Developed by **@undrrwrldd**

This mod targets **1.21.11**. To rebuild for another revision, change `minecraft_version`, `yarn_mappings` and `fabric_version` in `gradle.properties`.

## Features

- Pick any image from your computer (PNG, JPG, GIF, BMP, WEBP)
- Animated GIF support (frames replay according to GIF delays)
- Live preview while editing
- Scale slider (0.1x - 5x)
- Offset X / Y sliders for cropping the visible portion
- Toggle GIF animation on/off (use first frame as static)
- Save unlimited named presets to `config/customshields/presets.json`
- Active preset auto-applies on game start

## Controls

- **K** — Open the Custom Shields GUI (rebindable in `Options → Controls`)

## Build

Requires JDK 21.

```
./gradlew build
```

The compiled mod jar will be in `build/libs/`. Drop it into your `mods/` folder along with [Fabric API](https://modrinth.com/mod/fabric-api).

## How it works

- A persistent `NativeImageBackedTexture` is registered at `customshields:shield/custom`.
- When a preset is active, a Mixin into `ShieldModelRenderer` cancels the vanilla render and re-runs it bound to the custom texture.
- The shield model is `64×64`, so any input image is rescaled and cropped to that layout (face = 12×22 at (2,0); handle = 4×12 at (0,22)).

## Credits

- **@undrrwrldd** — developer

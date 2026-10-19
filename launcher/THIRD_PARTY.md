# Asset and source provenance

## App icons

The five monochrome outlines come from [Arcticons-Team/Arcticons](https://github.com/Arcticons-Team/Arcticons/tree/917baa2b8b1c304e7a45de48d35e97b7a774c251/icons/white), commit `917baa2b8b1c304e7a45de48d35e97b7a774c251`. Upstream licenses its icon artwork under **CC BY-SA 4.0**. The license, original SVGs and per-file hashes are retained in `assets/icons/`.

`scripts/import-icons.py` adapts those paths to Android vectors: circles become equivalent arcs, white becomes `#F0F2F5`, and a 1-unit stroke becomes 1.1 for TV legibility. The adapted artwork remains under CC BY-SA 4.0. Marks remain their respective owners' marks. SmartTube intentionally uses the YouTube outline requested for this setup. No icon-pack APK or runtime service is bundled.

## Wallpaper

The GLSL source in `assets/wallpaper/` was extracted from the owner's [nuzair.dev website source](https://github.com/nuzair46/nuzair46.github.io), `src/components/Dashboard/Canvas.jsx`. The local renderer preserves its neural motion while adding grayscale compositing, a seamless 24-second time path, packed 16-bit output, transfer conversion and fine dithering.

The active video is `app/src/main/res/raw/neural.mp4`: 1920×1080, 30 fps, 720 frames, HEVC Main10, SDR BT.709 limited range, no audio. SHA-256: `8076dc4b05856ae138b4b1b201ec5aeca83d1641c9e0c47838207c70a2cc9e03`. It is rendered locally and embedded in the APK; the TV does not load a website.

The startup still, `app/src/main/res/drawable-nodpi/neural_poster.png`, is extracted from the first frame of that same video with BT.709-to-sRGB conversion; see [asset instructions](../docs/assets.md).

## Reference and platform

[Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack/tree/d7a2efcd84bf628ae131072f70620a03ad069383), commit `d7a2efcd84bf628ae131072f70620a03ad069383`, was studied for native launcher/Home-routing approaches. Its MIT notice is retained as `REFERENCE-LICENSE.txt`. No upstream APK or Outfit font is bundled. CustomTV's layout, data reader, video lifecycle and narrow HOME override were implemented locally.

Fonts are Android's platform sans-serif. No Apple/tvOS font, icon, software or private framework is bundled. Build tools are pinned official Android SDK/Build Tools and Eclipse Adoptium Temurin releases. No third-party runtime libraries, ad SDKs or analytics SDKs are used.

Platform references: [VectorDrawable](https://developer.android.com/reference/android/graphics/drawable/VectorDrawable), [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer), [TvContract](https://developer.android.com/reference/android/media/tv/TvContract), [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

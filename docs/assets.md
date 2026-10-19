# Rebuild assets

Normal APK builds use the committed vector XML and active MP4. Asset generation is optional and happens entirely on the host.

## Icons

```sh
python3 launcher/scripts/import-icons.py
```

This converts the five retained original SVGs into Android vectors. It does not download an icon pack or require an additional installed app. Provenance and licenses are beside the SVGs.

## Wallpaper

Requires Node.js with built-in `fetch`/WebSocket support, Chromium and FFmpeg with the `libx265` encoder. Start a separate temporary local renderer:

```sh
chromium --headless=new --no-first-run --no-default-browser-check \
  --disable-extensions --disable-background-networking \
  --remote-debugging-address=127.0.0.1 --remote-debugging-port=9227 \
  --user-data-dir=/tmp/customtv-render-precision --enable-unsafe-swiftshader about:blank
```

In another terminal:

```sh
node launcher/scripts/render-precise-wallpaper.mjs --preview
node launcher/scripts/render-precise-wallpaper.mjs
node launcher/scripts/render-precise-wallpaper.mjs --close-renderer
```

Outputs stay in ignored `launcher/build/wallpaper/`. The renderer refuses to overwrite an existing finished MP4. It reads GLSL from `launcher/assets/wallpaper/`; no private diagnostics or website checkout are needed.

The shader computes at high precision, packs each 16-bit grayscale value into two channels for lossless readback, converts sRGB through linear light to BT.709, and adds static fine dither. Encoding uses Main10 HEVC at 1080p30, a 24-second loop and no audio. This avoids the old 8-bit browser-image intermediary and incorrectly tagged transfer curve.

Review a new render before replacing `launcher/app/src/main/res/raw/neural.mp4`. Update the expected SHA-256 in `launcher/scripts/build.py` and the provenance documentation at the same time. Different GPU/encoder versions may produce different bytes. The active file's digest is a verification pin, not a claim of universally bit-identical rendering.

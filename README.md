# tv-dotenv

CustomTV 0.1.3: a lightweight native Android TV launcher with a tvOS-inspired monochrome interface, an animated neural background and remote-friendly navigation.

- Icon-only shortcuts for Nuvio, SmartTube, Spotify, Steam Link and Play Store. Newly installed user TV apps appear automatically.
- Fixed Apps, HDMI Inputs and Continue Watching rows, plus a clock. No vertical Home scrolling.
- Minimal sidebar for Inputs, Wi-Fi, Bluetooth, Picture, Sound, Appearance and full TV settings.
- Hardware-decoded 1080p30 wallpaper. The decoder releases when Home pauses; the idle Home view keeps the clock and video.
- An optional accessibility service handles Home, gear and the observed Xiaomi remote shortcuts. It cannot read screen contents or perform gestures.

The launcher does not require Projectivy or root. It preserves the stock launcher, native settings and hardware services. Tested on Xiaomi MiTV-MZTU0 (`river`), Android 14; native settings components and branded remote codes are firmware-specific.

## Build

Host tools: Linux x86-64, Python 3, `curl` and ADB. The bootstrap script downloads pinned, checksum-verified Android SDK/Build Tools and Temurin JDK archives into the ignored `launcher/toolchain/` directory.

```sh
python3 launcher/scripts/bootstrap.py
python3 launcher/scripts/build.py
```

Output: `launcher/build/nuzair-home.apk` and its SHA-256 file. The build uses native Java/Android APIs without Gradle, Compose, a WebView or third-party runtime libraries. The active video is included once, under `launcher/app/src/main/res/raw/`.

On the first build, a local signing key and password are created under `launcher/`. **Keep both private and backed up.** Updating an existing installation requires its original signing key. This working copy retains the original key locally; it is never committed. A fresh clone generates a different key unless the original is restored before building.

## Deploy to an identified TV

Pair/authorize ADB separately. Copy `.env.example` to `.env`, then fill in the explicit ADB endpoint, hardware serial, product and firmware fingerprint. These values must match the selected device. Environment variables override the local file. No device is selected automatically.

Read the identity from the explicitly selected endpoint:

```sh
adb devices -l
adb -s YOUR_TV_ENDPOINT shell getprop ro.serialno
adb -s YOUR_TV_ENDPOINT shell getprop ro.product.device
adb -s YOUR_TV_ENDPOINT shell getprop ro.build.fingerprint
```

```sh
python3 launcher/scripts/device.py status
python3 launcher/scripts/device.py install
python3 launcher/scripts/device.py activate
```

`install` replaces only the CustomTV APK. `activate` sets the HOME role, grants TV listings access for Continue Watching, and enables CustomTV's remote accessibility service while retaining unrelated services. It does not disable or uninstall any other package. Device identity and owner user 0 are checked before every action. Recovery snapshots and an append-only journal remain in ignored `.local/` storage.

On the tested firmware, stock HOME retains higher intent priority even when CustomTV holds the HOME role. The service intercepts physical Home and redirects only the exact stock HomeActivity as a fallback. If access is removed, enable **CustomTV · Remote buttons** under the TV's Accessibility settings. Verify this on the physical remote: ADB-injected keys do not follow the same filter path.

| Remote button | Action |
|---|---|
| Home | CustomTV |
| Gear / Menu | CustomTV sidebar |
| Netflix / Prime Video | Nuvio |
| YouTube | SmartTube |
| Apps | Play Store |
| Volume / mute / power | Native behavior |

The branded mappings are restricted to the observed Xiaomi remote vendor/product identifiers, avoiding gamepad conflicts. Required apps must be installed separately from trusted sources.

## Recovery

```sh
python3 launcher/scripts/device.py rollback
```

This restores the deployment snapshot's previous HOME, removes CustomTV's override, retains unrelated accessibility services and leaves the APK/data installed. It does not change picture controls, app package states or firmware. Keep `.local/` with your private backups. Snapshots made when CustomTV was already the prior HOME are not a substitute for an original migration backup.

The original TV-optimization backups, diagnostics and rollback scripts are archived outside this repository. On the owner's working machine, the ignored `LOCAL_RECOVERY.md` records their location and commands. They are intentionally not part of a clone.

## Assets and validation

The five app marks are Android vectors generated from the original Arcticons SVGs. The background is rendered offline from the owner's website shader with a 16-bit readback, proper sRGB-to-BT.709 conversion and fine dithering. No shader rendering runs on the TV.

- [Asset provenance and licenses](launcher/THIRD_PARTY.md)
- [Rebuilding icons and wallpaper](docs/assets.md)
- [Validation and measured performance](docs/validation.md)
- [Audited debloat scope](docs/debloat.md)

The repository contains source, active assets and build/deploy tools. Downloaded APKs, toolchains, signing keys, device identifiers, screenshots, viewing history, logs, backups and obsolete experiments are excluded. The original operational archive remains local.

# Validation and performance

CustomTV 0.1.3 was deployed and checked on Xiaomi MiTV-MZTU0 (`river`), Android 14 SDK 34. The Android UI remains 1920×1080, scaled by the 3840×2160 panel. No TV calibration, picture mode, backlight, HDR range or game-mode settings were changed by the visual refinement.

## Completed device checks

- 30 integration checks: five app launches and Home returns, fixed-row navigation, native/custom settings, a real Nuvio continuation link, all three HDMI passthrough routes, and critical Android service availability.
- 13 sidebar checks: native destinations, HDMI/CEC settings, wallpaper and reduced-motion toggles, ambient start/wake and system screensaver settings. Toggles were restored to wallpaper On and Reduce motion Off.
- Hardware playback through `c2.mtk.hevc.decoder`. A steady full-loop interval recorded 815 frames over 27.118 seconds, about 30 fps, without increasing the dropped-frame counter. The counter already included startup/seek drops; this is not a zero-drop claim for startup.
- A failed intermediate Home-return test exposed stock Home relaunching itself shortly after redirection. Physical-button debounce was separated from stock-window redirects; all five final app-to-Home tests passed.
- The earlier physical remote test confirmed Home, gear, Netflix, Prime Video, YouTube and Apps mappings after a normal reboot. Those mappings remain in this version. There was no additional reboot solely for the visual refinement.
- The user confirmed on the physical TV: **“Less washed out; gradients look better.”** A background-only capture region's dominant dark value changed from about 20 to 7/255 and stayed near 7 after settings returns. This is not a physical-panel banding score.

Raw device logs, screenshots, package inventories and recovery snapshots remain in the owner's private operational archive, outside this repository. They contain device and viewing information and are not needed to build the launcher.

## Short before/after measurements

Three resource windows and three process-cold activity starts per version, on the same boot. App integration tests occurred between conditions. Figures are observed medians; background state and caches differ.

| Metric | 0.1.2 | 0.1.3 |
|---|---:|---:|
| Launcher PSS | 28,116 KiB / 27.5 MiB | 28,291 KiB / 27.6 MiB |
| Launcher CPU, fraction of all-core capacity | 1.17% | 1.27% |
| Whole-system CPU busy | 28.25% | 27.33% |
| Available system RAM | 585,148 KiB | 576,364 KiB |
| All system process count | 400 | 424 |
| Process-cold activity start | 447 ms | 527 ms |
| Signed deployed APK size | 3,146,830 bytes | 9,659,420 bytes |

The higher-quality video accounts for most APK growth. It runs at 3.20 Mbps versus 1.02 Mbps. The offline shader does not run on the TV. Cold-start samples were 733/447/392 ms before and 814/443/527 ms after; the median is 80 ms slower, with overlapping ranges. No new performance gain is claimed from the visual changes. Launcher memory remains about 28 MiB.

These measurements predate repository cleanup; native application code and active assets are unchanged by that cleanup. A build on another host may have a different signed APK digest.

## Remaining limits

Launch/routing checks do not prove app authentication, sustained playback, Cast playback, audio quality, HDMI signal quality, HDR/ALLM/CEC or long gaming sessions. Those require the actual TV and connected devices. Native settings components and remote codes may differ on another firmware. Residual banding and icon-edge quality remain visual judgments; native 4K UI rendering and perfect panel output are not claimed.

## 0.1.4 wallpaper return (2026-10-20)

A density-independent first-frame still now covers decoder preparation, and playback starts at the beginning without a resume seek. The original Main10 video and picture controls are unchanged. The decoder still releases on pause; the poster adds one 1080p image allocation (approximately 8 MiB uncompressed RGBA).

The signed APK built and passed signature verification, then updated the existing installation while retaining app data. Three Settings-to-Home checks confirmed decoder release outside Home, poster dismissal on the first-frame notification, and hardware HEVC playback after return. Measured preparation-to-first-frame times were **442, 295 and 326 ms**; these are not total remote-button-to-screen latencies or a controlled before/after comparison. A Nuvio-to-Home check also confirmed release and resumed playback, with a 413 ms first-frame notification. Each short playback sample reported zero dropped frames. A direct 1080p screenshot now includes the video background and is used in the README.

The initial automated attempt was invalid because the system screensaver was foreground; the results above were collected after dismissing it. Physical confirmation of a seamless still-to-video transition remains necessary.

# Audited debloat scope

This repository's deployment script does **not** disable, uninstall or suspend packages. The original device audit and exact-state rollback data remain in the private operational archive. A clone has no original package-state baseline and must not assume one.

The configured TV previously had these thirteen explicitly audited packages disabled with app data retained:

| Package | Reason on the inspected TV |
|---|---|
| `com.amazon.amazonvideo.livingroom` | Unused Prime Video client |
| `com.netflix.ninja` | Unused Netflix client; firmware may re-enable it |
| `com.sonyliv` | Unused Sony LIV client |
| `com.graymatrix.did` | Unused ZEE5 client |
| `com.google.android.youtube.tv` | Stock YouTube client replaced in this setup by SmartTube |
| `com.google.android.youtube.tvmusic` | Unused YouTube Music client |
| `com.google.android.play.games` | Unused Play Games app |
| `com.xiaomi.mitv.mediaexplorer` | Optional media file browser |
| `com.xiaomi.mimusic2` | Optional local music player; Spotify retained |
| `in.startv.hotstar` | Unused streaming client |
| `com.mitv.tvhome.michannel` | Audited optional channel/recommendation provider |
| `com.mitv.tvhome.oemtab` | Audited OEM promotional tab provider |
| `com.google.android.feedback` | Optional feedback/report uploader |

This is a record of inspected packages on one firmware, not a generic removal script. Package names alone do not prove a component is safe on another device. The factory-disabled input package retained its original state.

Stock HOME, System UI, Settings, Google services/Play Store, Cast, firmware updates, networking, Bluetooth/remote, HDMI/CEC, audio, DRM and Xiaomi hardware integration remain enabled. No background-process-limit, thermal, CPU-governor, Play Protect or system-partition changes were made. The custom launcher's minimal app row does not itself disable applications.

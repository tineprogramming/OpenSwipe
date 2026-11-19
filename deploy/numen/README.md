# OpenSwipe for Numen Air U50 (Android 11)

Download `OpenSwipe-numen.apk`, `openswipe.json` and `install.sh` from the
**Latest build (dev)** pre-release (or from the workflow run's artifacts).

```sh
./install.sh OpenSwipe-numen.apk openswipe.json
```

What this build changes compared to upstream:

- **Keyboard-safe:** the bottom-edge Home gesture pauses while the soft keyboard is
  open, so the space/enter row can be tapped. Turned on by default
  (`disableBottomOnIme`). Side gestures keep working (`disableSidesOnIme`, off by default).
- **Default gestures unchanged:** left=Back, right=Back, bottom=Home, all swipe-only.
- **English UI.**
- **Config file:** the app imports `openswipe.json` at startup when the file is new or has
  changed. It looks in these places, in order:
  1. `/sdcard/Android/data/com.openswipe/files/openswipe.json` (no permission needed)
  2. `/sdcard/openswipe.json` or `/sdcard/Download/openswipe.json` (after
     `adb shell appops set com.openswipe MANAGE_EXTERNAL_STORAGE allow`)

  To import or export while the app is running:
  ```sh
  adb shell am broadcast -a com.openswipe.action.IMPORT_CONFIG -n com.openswipe/.config.ConfigReceiver
  adb shell am broadcast -a com.openswipe.action.IMPORT_CONFIG -n com.openswipe/.config.ConfigReceiver --es path /sdcard/openswipe.json
  adb shell am broadcast -a com.openswipe.action.EXPORT_CONFIG -n com.openswipe/.config.ConfigReceiver
  ```
  The Settings screen also has Import and Export buttons.
- **Fixed:** an edit made on the rule detail screen was lost or reset to the default
  preset after pressing Apply, because each screen had its own ViewModel.

The APK is signed with the key in `keystore/`, which differs from upstream. The first
install over upstream OpenSwipe 1.0.2 has to uninstall it first; `install.sh` does this.

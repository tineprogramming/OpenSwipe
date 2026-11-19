# Changelog (fork)

## 1.0.5-fork-numen (versionCode 5)
- **Fixed: the outer keyboard keys (q, p, a, l, z, m) couldn't be tapped.** On the
  240px-wide screen the left and right edge strips (about 11px) sat on top of those keys,
  and tap pass-through can't reach the keyboard window. Now, while the soft keyboard is
  open, the left and right strips stop at the keyboard's top edge. Back still works above
  the keyboard and every key is free. The strips go back to full height when the keyboard
  hides, and follow it if it changes height.
  - The keyboard's top edge comes from `TYPE_INPUT_METHOD` window bounds on
    window-change events.
  - If the keyboard covers nearly the whole screen (less than 24px left above it), the side
    strips are removed while it is open.
- Unchanged: the bottom edge still pauses while the keyboard is open, and the g4home lock
  screen blocklist, package, signing key (installs over 1.0.4 with `pm install -r`),
  minSdk 26 and default preset all stay the same.

## 1.0.4-fork-numen (versionCode 4)
- **Gestures pause in front of blocked activities.** While a blocked app or activity is
  in front, all edge sensors are removed, so touches go straight to that app. The same
  mechanism already pauses the bottom edge for the keyboard. The sensors come back when
  another activity comes to the front.
  - Default blocklist (numen): the `com.glocalme.g4home` lock screen
    (`com.glocalme.basic.lock.LockScreenActivity`, `FirstLockScreenActivity`). Its "swipe up
    to unlock" gesture starts in the bottom Home zone, so unlocking opened the launcher.
  - The list can be edited in Settings → "Paused in these apps", or with
    `settings.blockedActivities` in `openswipe.json`. Each entry is `pkg/Class`,
    `pkg/.Class` or `pkg` (the whole app).
  - The foreground app is detected from `TYPE_WINDOW_STATE_CHANGED`. Only real activity
    classes count, checked with PackageManager, so dialog and view events are ignored.
    The app now declares `QUERY_ALL_PACKAGES` so it can check other apps' activities on
    Android 11+.
- Same package, same signing key as 1.0.3 (cert SHA-256 `C6:17:1C:…:46:40:26`), so it
  installs over 1.0.3 with `pm install -r`.
- Unchanged: default preset, keyboard suppression, 20dp bottom zone, minSdk 26.

## 1.0.3-fork-numen
- Taps and long presses on the edge strips pass through to the app below
  (`canPerformGestures`).
- The bottom gesture pauses while the keyboard is open.
- JSON config import/export, English UI, rule-edit save fix, 20dp bottom zone.

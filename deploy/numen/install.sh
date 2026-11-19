#!/bin/sh
# Install + configure OpenSwipe on Numen Air U50 over adb.
# Usage: ./install.sh [OpenSwipe-numen.apk] [openswipe.json]
set -e
APK="${1:-OpenSwipe-numen.apk}"
CFG="${2:-$(dirname "$0")/openswipe.json}"
PKG=com.openswipe
SVC=$PKG/com.openswipe.service.GestureAccessibilityService

adb push "$APK" /data/local/tmp/openswipe.apk
# glocalme.appstore is the whitelisted installer on this device.
if ! adb shell pm install -i com.glocalme.appstore -r /data/local/tmp/openswipe.apk; then
  # The upstream (Chinese) build is signed with another key: remove it and retry.
  echo "Install failed (signature mismatch?) - uninstalling old $PKG and retrying"
  adb uninstall "$PKG" || true
  adb shell pm install -i com.glocalme.appstore -r /data/local/tmp/openswipe.apk
fi
adb shell rm -f /data/local/tmp/openswipe.apk

# Permissions / keep-alive
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow || true
adb shell dumpsys deviceidle whitelist +$PKG

# Config file (optional — defaults are already keyboard-safe)
if [ -f "$CFG" ]; then
  adb shell mkdir -p /sdcard/Android/data/$PKG/files || true
  adb push "$CFG" /sdcard/Android/data/$PKG/files/openswipe.json || \
    adb push "$CFG" /sdcard/openswipe.json
fi

# Enable accessibility service (off -> on, so the service rebinds with the new
# capabilities, e.g. canPerformGestures for passing taps through the edge sensors)
adb shell settings put secure enabled_accessibility_services null
sleep 1
adb shell settings put secure enabled_accessibility_services $SVC
adb shell settings put secure accessibility_enabled 1

# Apply the config now (the app also imports it on its next start)
adb shell am broadcast -a com.openswipe.action.IMPORT_CONFIG -n $PKG/.config.ConfigReceiver
echo "Done."

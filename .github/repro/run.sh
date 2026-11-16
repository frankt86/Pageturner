#!/usr/bin/env bash
# Temporary: reproduces opening an EPUB with the new book engine.
set -x
APK="$1"
OUT="$2"
PKG=de.ph1b.audiobook.debug
mkdir -p "$OUT"
adb root
sleep 3
adb wait-for-device
adb install -r -g "$APK"
adb shell mkdir -p /sdcard/Audiobooks/Book
adb push baselineprofile/src/main/assets/Audiobooks/Book/01.m4a /sdcard/Audiobooks/Book/
adb push baselineprofile/src/main/assets/Audiobooks/Book/02.m4a /sdcard/Audiobooks/Book/
adb push book.epub /sdcard/Audiobooks/alice.epub
adb shell am start -W -n $PKG/voice.app.MainActivity
sleep 8
adb shell am force-stop $PKG
APP_UID=$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')
adb shell mkdir -p /data/data/$PKG/files/datastore
echo '{"newBookEngine":true}' > /tmp/readerSettings
adb push /tmp/readerSettings /data/data/$PKG/files/datastore/readerSettings
adb shell chown -R "$APP_UID:$APP_UID" /data/data/$PKG/files
adb shell restorecon -R /data/data/$PKG/files
adb shell cat /data/data/$PKG/files/datastore/readerSettings
adb logcat -c
adb logcat -b all -c
python3 .github/repro/drive.py "$PKG" "$OUT"
status=$?
adb logcat -d > "$OUT/logcat.txt"
adb logcat -b crash -d > "$OUT/crash.txt"
echo "===== CRASH BUFFER ====="
cat "$OUT/crash.txt"
echo "===== FATAL / AndroidRuntime ====="
grep -n -A80 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -200
echo "===== app warnings ====="
PID_LINES=$(grep -iE "readium|Pageturner|voice\." "$OUT/logcat.txt" | tail -100)
echo "$PID_LINES"
echo "===== death ====="
adb logcat -d -b all > "$OUT/all.txt"
APP_PID=$(grep -o "Start proc [0-9]*:$PKG/" "$OUT/all.txt" | tail -1 | grep -o "[0-9]*" | head -1)
echo "pid $APP_PID"
grep -E "^\S+ \S+ +$APP_PID |lowmemorykiller|lmkd|Process $PKG|has died|am_proc_died|am_kill|tombstone|DEBUG +:|chromium|Render" "$OUT/all.txt" | grep -v Dialer | tail -250
adb shell ls -la /data/tombstones
grep -E "ReaderViewModel|ReadiumReader" "$OUT/all.txt" | cut -c1-300
echo "drive status $status"
if [ "$status" = 0 ] && ! grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then echo "RESULT: the EPUB opened without a crash"; exit 0; fi
echo "RESULT: crash or failure"
exit 1

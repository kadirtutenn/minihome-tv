#!/usr/bin/env bash
# Device smoke/stability test. Usage: tools/smoke.sh [all|zap|cold|home|rapid|sleep|net|prefs|long]
set -u
PKG=com.minihome.tv
ACT=$PKG/.MainActivity
A="adb shell"
log()   { echo "== $*"; }
mem()   { $A dumpsys meminfo $PKG | awk '/TOTAL PSS:/{print "PSS(KB) " $3}/TOTAL:/{print "PSS(KB) " $2; exit}' | head -1; }
objs()  { $A dumpsys meminfo $PKG | grep -E "Activities:|Views:" | tr -s ' '; }
codecs(){ $A dumpsys media.player 2>/dev/null | grep -c "Client" ; }
start() { $A am start -W -n $ACT | grep TotalTime; }
crashes(){ adb logcat -d -b crash | grep -c "$PKG" ; }
key()   { $A input keyevent "$@"; }

zap() { # every channel must reach PLAYING
  adb logcat -c; start >/dev/null; sleep 6
  n=$(python3 -c "import json;print(len(json.load(open('app/src/main/assets/channels.json'))))")
  for i in $(seq 1 "$n"); do key CHANNEL_UP; sleep 9; done
  adb logcat -d -s PlayerController:* | grep -E ": (PLAYING|UNAVAILABLE)|step" | sort -u -k6
}
cold()  { for i in $(seq 1 10); do $A am force-stop $PKG; start; done; sleep 5; mem; }
home()  { start >/dev/null; sleep 5; mem; for i in $(seq 1 20); do key HOME; sleep 1; start >/dev/null; sleep 2; done; sleep 5; mem; objs; }
rapid() { start >/dev/null; sleep 4; for i in $(seq 1 60); do key CHANNEL_UP; sleep 0.15; done; for i in $(seq 1 60); do key CHANNEL_DOWN; done; sleep 10; mem; objs
          adb logcat -d -s PlayerController:* | tail -3; }
sleepwake() { start >/dev/null; sleep 5; for i in $(seq 1 5); do key SLEEP; sleep 3; key WAKEUP; sleep 1; $A wm dismiss-keyguard; sleep 6; done; mem; objs
          adb logcat -d -s PlayerController:* | tail -2; }
net()   { adb logcat -c; start >/dev/null; sleep 6; $A svc wifi disable; $A svc data disable; sleep 40
          adb logcat -d -s PlayerController:* | tail -4; $A svc wifi enable; $A svc data enable; sleep 20; adb logcat -d -s PlayerController:* | tail -2; }
prefs() { $A am force-stop $PKG
          printf '<?xml version="1.0"?><map><int name="last_channel" value="7"/><string name="favorites">x</string>garbage<' > /tmp/state.xml
          adb push /tmp/state.xml /data/local/tmp/state.xml >/dev/null
          adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/state.xml shared_prefs/state.xml'"
          adb logcat -c; start; sleep 8; adb logcat -d | grep -E "PlayerController|ChannelRepository|FATAL" | tail -4
          printf '<?xml version="1.0"?><map><int name="last_channel" value="7"/></map>' > /tmp/state.xml
          adb push /tmp/state.xml /data/local/tmp/state.xml >/dev/null
          adb shell "run-as $PKG cp /data/local/tmp/state.xml shared_prefs/state.xml"
          $A am force-stop $PKG; adb logcat -c; start; sleep 8; adb logcat -d | grep -E "PlayerController|ChannelRepository|FATAL" | tail -4; }
long()  { start >/dev/null; sleep 5; mem; for i in $(seq 1 7); do sleep 300; mem; done; adb logcat -d -s PlayerController:* | tail -3; }

case "${1:-all}" in
  all) for t in cold home rapid sleepwake prefs net zap; do log $t; $t; done; log "crash count: $(crashes)";;
  sleep) sleepwake;; *) "$1"; log "crash count: $(crashes)";;
esac

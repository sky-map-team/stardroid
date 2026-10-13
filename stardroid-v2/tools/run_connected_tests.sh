#!/usr/bin/env bash
# Runs the connected test suite for android.yml, on the emulator android-emulator-runner has
# already booted. Usage: run_connected_tests.sh <attempt>
#
# RendererPerfTest runs last, in a pass of its own: the hosted runner's emulator sometimes dies
# while that class tears down a GL surface (D140), and a lost emulator must not take the rest of
# the suite with it. Each pass ends as pass, tests-failed or emulator-lost. android.yml retries
# an emulator-lost attempt (up to attempt 3) on a fresh emulator, re-running only the passes that
# didn't pass on any earlier attempt.
#
# Writes build/ci/attempt-<attempt>/: each pass's Gradle log, reports and result, the logcat,
# and the attempt's overall `outcome`.

set -uo pipefail

attempt=${1:?usage: run_connected_tests.sh <attempt>}
out=build/ci/attempt-$attempt
renderer_test=com.google.android.stardroid.testscene.RendererPerfTest

# An attempt that died while installing the SDK leaves no results, so look at every earlier one.
passed_earlier() {
  local i
  for ((i = attempt - 1; i >= 1; i--)); do
    [[ $(cat "build/ci/attempt-$i/$1.result" 2>/dev/null) == pass ]] && return 0
  done
  return 1
}

emulator_alive() { timeout 30 adb shell true >/dev/null 2>&1; }

# Skips the root build/, which holds build/ci.
report_dirs() {
  find . -path ./build -prune -o -type d -path '*/build/reports/androidTests' -prune -print
}

run_pass() {
  local name=$1
  shift
  if passed_earlier "$name"; then
    echo "== $name: passed on an earlier attempt, not re-running"
    echo pass >"$out/$name.result"
    return
  fi
  local result
  if ! emulator_alive; then
    result=emulator-lost
  else
    # Both passes write :app's report directory, so start each from empty and keep a copy.
    report_dirs | xargs rm -rf
    if timeout 1200 ./gradlew "$@" -x lint --stacktrace 2>&1 | tee "$out/$name-gradle.log"; then
      result=pass
    elif emulator_alive; then
      result=tests-failed
    else
      result=emulator-lost
    fi
    report_dirs | while read -r dir; do
      mkdir -p "$out/$name-reports/${dir#./}"
      cp -R "$dir/." "$out/$name-reports/${dir#./}"
    done
  fi
  echo "$result" >"$out/$name.result"
  echo "== $name: $result"
}

mkdir -p "$out"
timeout 120 adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb logcat -c
adb logcat -G 16M
adb logcat >"$out/logcat.log" 2>&1 &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null' EXIT

# :app is flavored (D49): its instrumented tests run as the fdroid variant (no Play Services
# needed on the CI emulator); the library modules still answer to the unflavored task name.
run_pass main connectedFdroidDebugAndroidTest connectedDebugAndroidTest \
  -PtestNotClass="$renderer_test"
# skipGlBenchmarks skips the D19 GL benchmark gate and its D67 AR variant: the hosted emulator
# can't survive the 100k-point continuous scene, so they're verified locally instead (D105).
# The test logs a loud banner when this flag disables it.
run_pass renderer :app:connectedFdroidDebugAndroidTest \
  -PtestClass="$renderer_test" -PskipGlBenchmarks=true

results=$(cat "$out/main.result" "$out/renderer.result")
if grep -qx tests-failed <<<"$results"; then
  outcome=tests-failed
elif grep -qx emulator-lost <<<"$results"; then
  outcome=emulator-lost
else
  outcome=pass
fi
echo "$outcome" >"$out/outcome"
echo "== attempt $attempt: $outcome"
[[ $outcome == pass ]]

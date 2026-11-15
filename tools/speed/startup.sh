#!/bin/sh
# Runs the app's start-up timing (app/src/test/.../StartupSpeedTest.kt) N times, each in a fresh JVM, and prints each run's figures.
# Needs JAVA_HOME and ROBOLECTRIC_DEPS as in README.md. Usage: tools/speed/startup.sh [runs, default 5]
cd "$(dirname "$0")/../.."
for i in $(seq 1 "${1:-5}"); do
  ./gradlew :app:testDebugUnitTest --tests "clean.app.StartupSpeedTest" --rerun -q > /dev/null 2>&1
  grep -E ' ms$|not loaded' app/build/test-results/testDebugUnitTest/TEST-clean.app.StartupSpeedTest.xml | tr '\n' ';'
  echo
done

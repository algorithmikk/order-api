#!/bin/sh
# Builds a JDK AOT cache for the application jar at image build time.
#
# Runs in the final image, so the JVM that trains the cache is the same binary, on the
# same classpath, in the same directory as the JVM that will serve traffic.
#
# Training reaches context refresh only, via spring.context.exit=onRefresh. No AWS
# credentials exist during an image build, so this depends on secret loading having been
# moved off the refresh path onto ApplicationReadyEvent.
#
# Two cache flavours are attempted, best first:
#
#   1. The default, which includes AOT-linked classes. Best startup, but on amd64
#      containers this produced a cache that mapped cleanly - every region mapped, full
#      module graph enabled, relocation delta zero - and then failed VM init with
#      "Unexpected exception when loading aot-linked classes" and a message-less
#      InternalError. The same cache is fine on arm64, so it cannot be reproduced or
#      diagnosed off the build machine.
#   2. -XX:-AOTClassLinking, which keeps the archived classes and heap but drops the
#      pre-linking step that failed above. Smaller win, but it avoids the failing path.
#
# Each candidate is accepted only after booting with it, so a cache that cannot be used
# is never shipped. If both fail the image ships with no cache and the entrypoint boots
# normally: a slower start is an acceptable outcome for an optimisation, a container that
# will not start is not.
set -eu

JAR=app.jar
CACHE=app.aot
CONF=app.aotconf
LOG=/tmp/aot-verify.log
TRAIN_LOG=/tmp/aot-train.log

cleanup() { rm -f "$CONF" "$LOG" "$TRAIN_LOG"; }
trap cleanup EXIT

# Boots with the candidate cache exactly as the entrypoint will.
verify() {
  java -XX:AOTCache="$CACHE" -Xlog:aot=info:file="$LOG" \
    -Dspring.context.exit=onRefresh -jar "$JAR" >/dev/null 2>&1
}

echo "AOT: recording training run"
if ! java -XX:AOTMode=record -XX:AOTConfiguration="$CONF" \
     -Dspring.context.exit=onRefresh -jar "$JAR" >"$TRAIN_LOG" 2>&1; then
  echo "AOT: training run failed; shipping without a cache" >&2
  # The first failed builds left no reason in the log because stderr was discarded.
  # Print the Spring / JVM failure so the next attempt can be fixed without another
  # round of guessing.
  grep -iE "ERROR|Exception|Caused by:|APPLICATION FAILED" "$TRAIN_LOG" 2>/dev/null | tail -20 >&2 || true
  rm -f "$CACHE"
  exit 0
fi

for linking in "" "-XX:-AOTClassLinking"; do
  label=${linking:-default}
  echo "AOT: creating cache ($label)"

  if ! java -XX:AOTMode=create -XX:AOTConfiguration="$CONF" -XX:AOTCache="$CACHE" \
       ${linking} -jar "$JAR" >/dev/null 2>&1 || [ ! -s "$CACHE" ]; then
    echo "AOT: creation failed ($label)" >&2
    continue
  fi

  if verify; then
    echo "AOT: cache accepted ($label, $(du -h "$CACHE" | cut -f1))"
    exit 0
  fi

  echo "AOT: verification boot failed ($label)" >&2
  grep -iE "error|warning" "$LOG" 2>/dev/null | tail -5 >&2 || true
done

echo "AOT: no usable cache; shipping without one" >&2
rm -f "$CACHE"

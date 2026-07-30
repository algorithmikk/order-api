#!/bin/sh
# Launches the application jar, using the JDK AOT cache when the image build produced one.
#
# The cache in /app/app.aot is trained during the image build against this exact jar and
# classpath. The build verifies it by booting with it, and deletes it if that fails, so the
# file being present already means it was proven usable in this image.
#
# The guard still matters: it keeps this entrypoint working for an image built without a
# cache, where the alternative would be failing at VM init on a missing archive.
set -e

AOT_OPTS=""
if [ -s /app/app.aot ]; then
  AOT_OPTS="-XX:AOTCache=/app/app.aot"
fi

# shellcheck disable=SC2086
exec java ${AOT_OPTS} ${JAVA_OPTS:-} -jar /app/app.jar "$@"

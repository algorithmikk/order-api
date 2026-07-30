#!/bin/sh
# Extracted Spring Boot layers (JarLauncher). AOT Cache is NOT enabled here.
#
# Professional AOT (when ready): follow Spring Boot Dockerfiles AOT section —
# train fail-closed with -XX:AOTCacheOutput=app.aot on extracted application.jar,
# then always pass -XX:AOTCache=app.aot. See api/test/perf/AOT-CACHE.md.
#
# Do not opt into a best-effort / empty cache; that crashed JDK 26 at VM init.
set -e
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} org.springframework.boot.loader.launch.JarLauncher "$@"

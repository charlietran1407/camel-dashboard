#!/bin/sh
set -e

if [ "$SKIP_INITIAL_PHASE" = "true" ]; then
    echo "==> Skipping Phase 1 (SKIP_INITIAL_PHASE is set to true)"
else
    echo "==> Starting Phase 1: Resolving and downloading Camel route dependencies..."
    java -Dspring.profiles.active=prod -jar app.jar --initial
fi

echo "==> Starting Phase 2: Launching Apache Camel Dashboard..."
exec java -Dspring.profiles.active=prod -Dloader.path=/app/libs -jar app.jar

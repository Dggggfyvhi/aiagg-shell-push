#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_URL=$(grep distributionUrl "$APP_HOME/gradle/wrapper/gradle-wrapper.properties" | cut -d= -f2)
DIST_HASH=$(echo "$DIST_URL" | md5sum | cut -d" " -f1)
DIST_DIR="$GRADLE_USER_HOME/dists/$DIST_HASH"
DIST_ZIP="$DIST_DIR/gradle-8.2-bin.zip"
if [ ! -f "$DIST_ZIP" ]; then mkdir -p "$DIST_DIR" && curl -fsSL "$DIST_URL" -o "$DIST_ZIP"; fi
cd "$DIST_DIR"
unzip -q -o gradle-8.2-bin.zip
exec "$DIST_DIR/gradle-8.2/bin/gradle" assembleDebug "$@"

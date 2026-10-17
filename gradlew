#!/bin/sh
set -eu
APP_HOME=$(cd "$(dirname "$0")" && pwd)
cd "$APP_HOME"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_URL=$(grep '^distributionUrl=' gradle/wrapper/gradle-wrapper.properties | cut -d= -f2)
if [ -z "$DIST_URL" ]; then
  echo "distributionUrl is not set in gradle/wrapper/gradle-wrapper.properties" >&2
  exit 1
fi
DIST_NAME=$(basename "$DIST_URL")
# gradle-8.2-bin.zip -> gradle-8.2
GRADLE_VER="${DIST_NAME%-bin.zip}"
GRADLE_VER="${GRADLE_VER%-all.zip}"
DIST_DIR="$GRADLE_USER_HOME/dist/$DIST_NAME"
DIST_ZIP="$DIST_DIR/$DIST_NAME"
if [ ! -f "$DIST_ZIP" ]; then
  mkdir -p "$DIST_DIR"
  curl -fsSL "$DIST_URL" -o "$DIST_ZIP"
fi
if [ ! -x "$DIST_DIR/$GRADLE_VER/bin/gradle" ]; then
  unzip -q -o "$DIST_ZIP" -d "$DIST_DIR"
fi
exec "$DIST_DIR/$GRADLE_VER/bin/gradle" "$@"

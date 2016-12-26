#!/usr/bin/env bash
set -efuxo pipefail

PATH=$(nix-build --no-out-link '<nixpkgs>' --attr gawk.out)/bin:$ANDROID_HOME/tools:$PATH
here=$(dirname "$0")

buildConfig=$here/fooCam/build.gradle
sdkVersion=$(awk '/compileSdkVersion/ {print $2}' "$buildConfig")
toolsVersion=$(awk -F '"' '/buildToolsVersion/ {print $2}' "$buildConfig")

echo y | android update sdk --no-ui \
    --filter "android-$sdkVersion,build-tools-$toolsVersion"

"$here/gradlew" build --stacktrace

#!/bin/bash

set -e

cd "$(dirname "$0")/.."

rm fooCam/lib/crittercism_v4_0_2_sdkonly.jar
sed -i '/Crittercism/d' fooCam/src/main/java/net/phunehehe/foocam/MainActivity.java

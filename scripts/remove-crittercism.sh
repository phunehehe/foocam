#!/bin/bash

set -e

cd "$(dirname "$0")/.."

rm fooCam/lib/crittercism_v4_0_2_sdkonly.jar
rm .idea/libraries/crittercism_v4_0_2_sdkonly.xml

sed -i '/Crittercism/d'                 fooCam/src/main/java/net/phunehehe/foocam/MainActivity.java
sed -i '/crittercism/d'                 fooCam/build.gradle
sed -i '/crittercism/d'                 fooCam/fooCam-fooCam.iml
sed -i '/android.permission.INTERNET/d' fooCam/src/main/AndroidManifest.xml

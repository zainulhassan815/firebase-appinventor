#!/usr/bin/env bash
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
secrets=${FIREBASE_SECRETS:-$here/secrets}
package=appinventor.ai_dreamerslab.PushDemo
appinventor=$("${RUSH_CLI:-$root/../rush-cli}/tool/e2e/buildserver.sh")
work=$root/.e2e/push-demo
screen=$work/project/src/appinventor/ai_dreamerslab/PushDemo

(cd "$root" && rush build --no-color >/dev/null && python3 -I samples/package.py)
rm -rf "$work" && mkdir -p "$work/project/assets"
unzip -q "$root/out/PushDemo.aia" -d "$work/project"
cp "$secrets/google-services.json" "$work/project/assets/"
sed -i 's/"GoogleServicesFile": ""/"GoogleServicesFile": "google-services.json"/' "$screen/Screen1.scm"
unzip -p "$root/out/dev.dreamerslab.firebase.aix" dev.dreamerslab.firebase/components.json > "$work/components.json"
node "$here/blocks/compile.js" "$appinventor" "$screen/Screen1.scm" "$screen/Screen1.bky" \
  "$work/components.json" "$package.Screen1" "$screen/Screen1.yail"
apk=$("$here/build-apk.sh" "$work")
SCREENSHOT=$work/failure.png PACKAGE=$package python3 -I "$here/drive.py" "$apk" "$here/sample.steps.json" \
  "$secrets/service-account.json"

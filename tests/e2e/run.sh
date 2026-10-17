#!/usr/bin/env bash
set -euo pipefail

scenario=$(realpath "${1:?usage: run.sh <scenario.yail>}")
here=$(cd "$(dirname "$0")" && pwd)
secrets=${FIREBASE_SECRETS:-$here/secrets}
name=$(basename "$scenario" .yail)
form=${scenario%.yail}.json
[ -f "$form" ] || form=$here/scenario.json
screen=$(mktemp --suffix=.yail)
steps=$(mktemp --suffix=.json)
trap 'rm -f "$screen" "$steps"' EXIT
render() { python3 -I "$here/render.py" "$secrets/google-services.json"; }
cat "$here/report.yail" "$here/events.yail" "$scenario" | render > "$screen"
render < "${scenario%.yail}.steps.json" > "$steps"
apk=$("$here/build-app.sh" "$name" "$form" "$screen" "$secrets/google-services.json" "$here"/assets/*)
SCREENSHOT=$(dirname "$(dirname "$apk")")/failure.png python3 -I "$here/drive.py" "$apk" "$steps" "$secrets/service-account.json"

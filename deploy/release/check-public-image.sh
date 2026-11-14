#!/usr/bin/env bash
# A public installation must not depend on the publisher's registry login.
set -euo pipefail
image="${1:?usage: check-public-image.sh IMAGE@DIGEST}"
configuration="$(mktemp -d)"
trap 'rm -rf "$configuration"' EXIT
docker --config "$configuration" manifest inspect "$image" >/dev/null
echo "image is readable without registry credentials: $image"

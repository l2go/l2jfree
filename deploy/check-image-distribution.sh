#!/usr/bin/env bash
# Copy the exact application tree from the image without starting its process.
set -euo pipefail
image="${1:?usage: check-image-distribution.sh IMAGE}"
directory="$(mktemp -d)"
container=""
cleanup() {
	local status=$?
	if [ -n "$container" ]; then docker rm "$container" >/dev/null || status=1; fi
	rm -rf "$directory" || status=1
	exit "$status"
}
trap cleanup EXIT
container="$(docker create --entrypoint /bin/true "$image")"
docker cp "$container:/opt/l2jfree/." "$directory"
python3 "$(dirname "$0")/check_distribution.py" "$directory"

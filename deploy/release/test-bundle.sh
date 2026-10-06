#!/usr/bin/env bash
# Checks the release bundle that bundle.sh builds. Needs bash and tar; validates
# the compose file with "docker compose config" when Docker Compose is present.
#
#   deploy/release/test-bundle.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

failures=0
expect() {
	local description=$1 expected=$2 actual=$3
	if [[ $actual != "$expected" ]]; then
		printf 'FAIL %s\n--- expected\n%s\n--- actual\n%s\n' "$description" "$expected" "$actual"
		failures=$((failures + 1))
	fi
}

digest=sha256:$(printf '%064d' 7)
image="ghcr.io/l2go/l2jfree:3.0.0@$digest"
"$here/bundle.sh" 3.0.0 "$image" "$work/out" >/dev/null

expect "archive and checksum" "l2jfree-3.0.0-compose.tar.gz l2jfree-3.0.0-compose.tar.gz.sha256" \
	"$(ls "$work/out" | paste -sd' ')"
expect "checksum matches" "l2jfree-3.0.0-compose.tar.gz: OK" "$(cd "$work/out" && sha256sum -c l2jfree-3.0.0-compose.tar.gz.sha256)"

tar -xzf "$work/out/l2jfree-3.0.0-compose.tar.gz" -C "$work"
bundle=$work/l2jfree-3.0.0
expect "bundle files" "README.md
compose.yaml
$(cd "$here/../postgres" && find init -type f | sed 's|^|postgres/|' | LC_ALL=C sort)" \
	"$(cd "$bundle" && find . -type f | sed 's|^\./||' | LC_ALL=C sort)"
expect "the image is pinned by digest" "  image: \${L2JFREE_IMAGE:-$image}" "$(grep '^  image: ${L2JFREE_IMAGE' "$bundle/compose.yaml")"
expect "init scripts stay executable" "yes" "$(for f in "$bundle"/postgres/init/*.sh; do [[ -x $f ]] && echo yes || echo "no $f"; done | sort -u | paste -sd' ')"
expect "no build context in the bundle" "" "$(grep -n 'build:' "$bundle/compose.yaml" || true)"

if docker compose version >/dev/null 2>&1 || command -v docker-compose >/dev/null; then
	compose=(docker compose)
	docker compose version >/dev/null 2>&1 || compose=(docker-compose)
	expect "compose accepts the bundle" "ghcr.io/l2go/l2jfree:3.0.0@$digest" \
		"$(cd "$bundle" && "${compose[@]}" -f compose.yaml config 2>/dev/null | sed -n 's/^ *image: \(ghcr.*\)/\1/p' | sort -u)"
fi

expect "a version and an image are required" "usage: bundle.sh VERSION IMAGE@DIGEST OUTDIR" "$("$here/bundle.sh" 2>&1 || true)"
expect "the image must carry a digest" "bundle.sh: the image must be pinned by digest (IMAGE@sha256:...)" \
	"$("$here/bundle.sh" 3.0.0 ghcr.io/l2go/l2jfree:3.0.0 "$work/x" 2>&1 || true)"

if ((failures)); then
	echo "$failures check(s) failed" >&2
	exit 1
fi
echo "bundle: all checks passed"

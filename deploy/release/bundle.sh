#!/usr/bin/env bash
# Builds the compose bundle of a release: everything a host needs besides
# Docker. The compose file names the released image by digest, so the bundle
# always starts exactly the image CI built, tested, and signed.
#
#   deploy/release/bundle.sh 3.0.0 ghcr.io/l2go/l2jfree:3.0.0@sha256:... out/
#
# writes out/l2jfree-3.0.0-compose.tar.gz and its .sha256.
set -euo pipefail

if (($# != 3)); then
	echo "usage: bundle.sh VERSION IMAGE@DIGEST OUTDIR" >&2
	exit 2
fi
version=$1 image=$2 out=$3
[[ $image == *@sha256:* ]] || {
	echo "bundle.sh: the image must be pinned by digest (IMAGE@sha256:...)" >&2
	exit 2
}

deploy=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
bundle=$work/l2jfree-$version

mkdir -p "$bundle/postgres"
cp -a "$deploy/postgres/init" "$bundle/postgres/"
cp "$deploy/release/README.md" "$bundle/README.md"
# The default image becomes the released one; L2JFREE_IMAGE still overrides it.
sed "s|^  image: \${L2JFREE_IMAGE:-[^}]*}|  image: \${L2JFREE_IMAGE:-$image}|" \
	"$deploy/compose.yaml" >"$bundle/compose.yaml"
grep -q "^  image: \${L2JFREE_IMAGE:-$image}$" "$bundle/compose.yaml" || {
	echo "bundle.sh: compose.yaml has no image line to pin" >&2
	exit 1
}

mkdir -p "$out"
archive=l2jfree-$version-compose.tar.gz
tar --sort=name --owner=0 --group=0 --numeric-owner --mtime=@0 -C "$work" -czf "$out/$archive" "l2jfree-$version"
(cd "$out" && sha256sum "$archive" >"$archive.sha256")
echo "$out/$archive"

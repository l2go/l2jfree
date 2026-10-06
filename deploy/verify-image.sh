#!/usr/bin/env bash
# Checks the content and the security posture of the built image.
# Usage: deploy/verify-image.sh IMAGE
set -euo pipefail

image="${1:?usage: verify-image.sh IMAGE}"

fail() { echo "image check failed: $*" >&2; exit 1; }
inside() { docker run --rm --entrypoint bash "$image" -c "$1"; }

platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image")"
[ "$platform" = "linux/amd64" ] || fail "the platform is $platform, expected linux/amd64"

user="$(docker image inspect --format '{{.Config.User}}' "$image")"
[ "$user" = "10001:10001" ] || fail "the image must run as 10001:10001, found '$user'"

inside '. /etc/os-release && [ "$ID" = arch ]' || fail "the base is not Arch Linux"
inside 'java -version 2>&1 | head -n1 | grep -q "\"25\."' || fail "the JRE is not version 25"
inside 'java -XshowSettings:properties -version 2>&1 | grep -q "java.vendor = Eclipse Adoptium"' \
	|| fail "the JRE is not Eclipse Temurin"

inside 'test -x /opt/l2jfree/bin/entrypoint && test -x /opt/l2jfree/bin/init-secrets' || fail "the scripts are missing"
inside 'test -f /opt/l2jfree/loginserver/config/loginserver.properties && test -f /opt/l2jfree/loginserver/config/logback.xml' \
	|| fail "the configuration is missing"

if inside 'ls /opt/l2jfree/loginserver/libs' \
	| grep -Eiq '(spring|hibernate|cglib|ehcache|c3p0|mchange-commons|ecj|irclib|commons-logging|jcl-over-slf4j|slf4j-jdk14)'; then
	fail "the image contains a removed library"
fi

if inside 'touch /opt/l2jfree/loginserver/write-test 2>/dev/null'; then
	fail "the application tree is writable by the service user"
fi

echo "image check passed: $image"

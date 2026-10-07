#!/usr/bin/env bash
# Checks the content and the security posture of the built image. The pipeline
# starts the stack and probes both ports afterwards.
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

inside 'test ! -e /usr/bin/captree' || fail "captree is in the image: its Go standard library has fixed vulnerabilities"
inside 'test -x /opt/l2jfree/bin/entrypoint && test -x /opt/l2jfree/bin/init-secrets' || fail "the scripts are missing"
for file in config/server.properties config/loginserver.properties config/logback.xml l2jfree-platform.jar \
	data/scripts.cfg catalog/npc_template.csv; do
	inside "test -s /opt/l2jfree/$file" || fail "$file is missing from the image"
done

# The world reads its datapack and catalog from the read-only tree (see the Dockerfile).
inside 'grep -qx "DatapackRoot = /opt/l2jfree" /opt/l2jfree/config/server.properties \
	&& grep -qx "CatalogDirectory = /opt/l2jfree/catalog" /opt/l2jfree/config/server.properties' \
	|| fail "the default data paths of the world do not name the image tree"

if inside 'ls /opt/l2jfree/libs' \
	| grep -Eiq '(spring|hibernate|cglib|ehcache|c3p0|mchange-commons|ecj|irclib|commons-logging|jcl-over-slf4j|slf4j-jdk14|liquibase|mysql-connector|javolution|trove4j|l2j-mmocore|graalpy|polyglot|truffle)'; then
	fail "the image contains a removed library"
fi

if inside 'touch /opt/l2jfree/write-test 2>/dev/null || touch /opt/l2jfree/config/write-test 2>/dev/null \
	|| touch /opt/l2jfree/data/write-test 2>/dev/null'; then
	fail "the application tree is writable by the service user"
fi

inside 'touch /var/lib/l2jfree/work/write-test /var/lib/l2jfree/config/write-test \
	&& rm /var/lib/l2jfree/work/write-test /var/lib/l2jfree/config/write-test' \
	|| fail "the work or the operator directory is not writable by the service user"

# The entry point refuses to start without its deployment settings and names the first missing one.
status=0
output="$(docker run --rm "$image" 2>&1)" || status=$?
[ "$status" -ne 0 ] || fail "the entry point started without the deployment settings"
grep -q "L2JFREE_DB_URL is required" <<< "$output" || fail "the entry point does not name the missing setting: $output"

echo "image check passed: $image"

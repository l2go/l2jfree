# syntax=docker/dockerfile:1

# The Platform 3.0 image. linux/amd64 on Arch Linux, pinned by dated tag and
# digest, with a Temurin 25 JRE archive pinned by SHA-256 (ADR-0002).
#
# This is the walking skeleton: it carries the LoginServer of the current code
# base. The world, PostgreSQL, and the single process arrive in milestone M2.

FROM archlinux:base-20261004.0.606936@sha256:4e77cf2ea5f410e6f8be5abf93ccf17ce2436e87a138c167208356711a405dbd AS jre

ADD --checksum=sha256:1731a34baadec5479258ea0202e4d5d865d2efeee60cb0c7d7eb056fe96ca219 \
    https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jre_x64_linux_hotspot_25.0.4.1_1.tar.gz \
    /tmp/jre.tar.gz
RUN mkdir -p /opt/jre && tar -xzf /tmp/jre.tar.gz -C /opt/jre --strip-components=1

FROM archlinux:base-20261004.0.606936@sha256:4e77cf2ea5f410e6f8be5abf93ccf17ce2436e87a138c167208356711a405dbd

RUN useradd --system --uid 10001 --user-group --home-dir /var/lib/l2jfree --shell /usr/bin/nologin l2jfree \
 && install -d -o 10001 -g 10001 -m 0750 /var/lib/l2jfree /var/lib/l2jfree/log /var/lib/l2jfree/secrets

COPY --from=jre /opt/jre /opt/jre
COPY loginserver /opt/l2jfree/loginserver
COPY --chmod=0755 bin/ /opt/l2jfree/bin/

ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:/usr/local/sbin:/usr/local/bin:/usr/bin

USER 10001:10001
WORKDIR /var/lib/l2jfree
EXPOSE 2106 7777
ENTRYPOINT ["/opt/l2jfree/bin/entrypoint"]

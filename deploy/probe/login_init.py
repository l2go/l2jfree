#!/usr/bin/env python3
"""Checks that a login server speaks the Lineage II login protocol.

The login server sends its first packet as soon as a client connects: a
two-byte little-endian length that counts itself, followed by the packet body.
The probe connects, reads that packet, and checks that the length is plausible
and that the whole body arrives. It retries until the timeout, so it can run
right after the stack starts.
"""

import argparse
import socket
import sys
import time

MIN_LENGTH = 8
MAX_LENGTH = 1024


def read_exactly(sock, count):
    data = b""
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise ConnectionError("the server closed the connection before the packet ended")
        data += chunk
    return data


def probe(host, port, timeout):
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        header = read_exactly(sock, 2)
        length = int.from_bytes(header, "little")
        if not MIN_LENGTH <= length <= MAX_LENGTH:
            raise ValueError(f"implausible first packet length {length}")
        read_exactly(sock, length - 2)
        return length


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=2106)
    parser.add_argument("--timeout", type=float, default=60.0, help="seconds to keep trying")
    args = parser.parse_args()

    deadline = time.monotonic() + args.timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            length = probe(args.host, args.port, 5.0)
            print(f"login probe: first packet of {length} bytes from {args.host}:{args.port}")
            return 0
        except (OSError, ValueError) as error:
            last_error = error
            time.sleep(2)
    print(f"login probe failed after {args.timeout:.0f}s: {last_error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())

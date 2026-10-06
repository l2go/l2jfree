#!/usr/bin/env python3
"""Measures how long the server takes to start under several JVM configurations.

Usage: deploy/measure/startup.py --image IMAGE [--runs 5] [--configs g1,zgc,g1-aot,zgc-aot] [--output DIR]

For each configuration the stack starts on empty volumes. A configuration without an AOT cache is measured
twice: the first start loads the catalog into the empty database (cold), then the server restarts `runs` times on
the loaded database (warm). A configuration with an AOT cache first runs once to write the cache, which is not
measured, and then restarts `runs` times with the cache. The time is the one the server reports in its own
"Platform ready in N ms" line, counted from the start of the JVM, so the time of Docker and of the health check
interval is not in it. After each start the script also reads the resident memory of the server process.

The result is a Markdown table (stdout, and DIR/startup.md) and DIR/startup.json. The measurement does not judge:
the decision is taken from the numbers (ADR-0012).
"""
import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import time

HEAP = "-Xms1g -Xmx3g"
READY = re.compile(r"Platform ready in (\d+) ms \(before main (\d+), login prepared (\d+), world started (\d+), "
                   r"login opened (\d+)\)")
AOT_FILE = "/var/lib/l2jfree/aot/server.aot"

CONFIGS = {
    "g1": {"gc": "-XX:+UseG1GC", "aot": False},
    "zgc": {"gc": "-XX:+UseZGC", "aot": False},
    "g1-aot": {"gc": "-XX:+UseG1GC", "aot": True},
    "zgc-aot": {"gc": "-XX:+UseZGC", "aot": True},
}

HERE = os.path.dirname(os.path.abspath(__file__))
COMPOSE = ["docker", "compose", "-p", "l2jfree-measure", "-f", os.path.join(HERE, "..", "compose.yaml"),
           "-f", os.path.join(HERE, "compose.yaml")]


def run(args, env=None, check=True, timeout=900):
    full = dict(os.environ)
    full.update(env or {})
    return subprocess.run(args, env=full, check=check, timeout=timeout, text=True, capture_output=True)


def compose(*args, env=None, check=True, timeout=900):
    return run(COMPOSE + list(args), env=env, check=check, timeout=timeout)


def ready_lines():
    logs = compose("logs", "--no-color", "server").stdout
    return [m for m in (READY.search(line) for line in logs.splitlines()) if m]


def wait_for_ready(count, seconds=300):
    deadline = time.time() + seconds
    while time.time() < deadline:
        found = ready_lines()
        if len(found) >= count:
            return found[count - 1]
        state = run(["docker", "inspect", "-f", "{{.State.Running}}", "l2jfree-measure-server-1"], check=False)
        if state.stdout.strip() == "false":
            raise SystemExit("the server stopped before it was ready:\n" + compose("logs", "--tail", "60", "server").stdout)
        time.sleep(1)
    raise SystemExit("the server was not ready after %d s:\n%s" % (seconds, compose("logs", "--no-color", "--tail", "80", "server").stdout))


def resident_megabytes():
    time.sleep(10)  # let the start settle: the JIT and the first collections run after the ports open
    status = run(["docker", "exec", "l2jfree-measure-server-1", "cat", "/proc/1/status"]).stdout
    kilobytes = int(re.search(r"VmRSS:\s+(\d+) kB", status).group(1))
    return round(kilobytes / 1024)


def sample(match):
    return {"total": int(match.group(1)), "main": int(match.group(2)), "login_prepared": int(match.group(3)),
            "world_started": int(match.group(4)), "login_opened": int(match.group(5)), "rss": resident_megabytes()}


def measure(name, image, runs):
    config = CONFIGS[name]
    options = "%s %s" % (HEAP, config["gc"])
    env = {"L2JFREE_IMAGE": image}
    print("== %s: %s" % (name, options), file=sys.stderr)
    compose("down", "-v", "--remove-orphans", check=False)
    compose("up", "-d", "--wait", "db", env=env)
    result = {"options": options, "cold": None, "warm": []}
    restarts = runs
    if config["aot"]:
        # the first start loads the catalog and writes the cache when the JVM exits; it is not measured
        compose("up", "-d", "--no-deps", "server", env=dict(env, L2JFREE_JAVA_OPTS=options + " -XX:AOTCacheOutput=" + AOT_FILE))
        wait_for_ready(1)
        compose("stop", "-t", "120", "server", env=env)
        listing = run(["docker", "run", "--rm", "-v", "l2jfree-measure_aot:/aot", "--entrypoint", "ls", image,
                       "-l", "/aot"]).stdout
        if "server.aot" not in listing:
            raise SystemExit("the training run wrote no AOT cache:\n" + listing)
        options += " -XX:AOTCache=" + AOT_FILE
        result["options"] = options
        env = dict(env, L2JFREE_JAVA_OPTS=options)
        # a new container, so its log starts empty: the first start with the cache is the first warm sample
        compose("up", "-d", "--no-deps", "--force-recreate", "server", env=env)
        result["warm"].append(sample(wait_for_ready(1)))
        restarts = runs - 1
    else:
        env = dict(env, L2JFREE_JAVA_OPTS=options)
        compose("up", "-d", "--no-deps", "server", env=env)
        result["cold"] = sample(wait_for_ready(1))
    seen = 1
    for _ in range(restarts):
        compose("restart", "-t", "60", "server", env=env)
        seen += 1
        result["warm"].append(sample(wait_for_ready(seen)))
    compose("down", "-v", "--remove-orphans", check=False)
    return result


def median(values):
    return int(statistics.median(values))


def table(results):
    rows = ["| Configuration | Cold start | Warm start, median | Warm start, range | Before main | World | Resident memory |",
            "|---|---:|---:|---:|---:|---:|---:|"]
    for name, result in results.items():
        warm = [w["total"] for w in result["warm"]]
        cold = "%.1f s" % (result["cold"]["total"] / 1000) if result["cold"] else "-"
        rows.append("| %s | %s | %.1f s | %.1f - %.1f s | %.1f s | %.1f s | %d MiB |" % (
            name, cold, median(warm) / 1000, min(warm) / 1000, max(warm) / 1000,
            median([w["main"] for w in result["warm"]]) / 1000,
            median([w["world_started"] for w in result["warm"]]) / 1000,
            median([w["rss"] for w in result["warm"]])))
    return "\n".join(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--image", required=True)
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--configs", default=",".join(CONFIGS))
    parser.add_argument("--output", default=".")
    args = parser.parse_args()
    names = args.configs.split(",")
    unknown = [n for n in names if n not in CONFIGS]
    if unknown:
        parser.error("unknown configuration: " + ", ".join(unknown))
    results = {name: measure(name, args.image, args.runs) for name in names}
    os.makedirs(args.output, exist_ok=True)
    with open(os.path.join(args.output, "startup.json"), "w") as out:
        json.dump(results, out, indent=2)
    markdown = table(results)
    with open(os.path.join(args.output, "startup.md"), "w") as out:
        out.write(markdown + "\n")
    print(markdown)


if __name__ == "__main__":
    main()

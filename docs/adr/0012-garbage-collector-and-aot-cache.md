# 0012. G1 as the garbage collector, no AOT cache in the image

- Status: Accepted
- Date: 2026-10-07

## Context

The vision of Platform 3.0 planned Generational ZGC and the JDK AOT cache in the image, on the expectation that pause time matters more than throughput on a long-lived process and that a stored warm-up shortens the start. Neither was measured on this code. The prototype's first numbers were close: 15 to 16 s with G1, 18 s with ZGC, 17 s with ZGC and the AOT cache.

The server is one process ([ADR-0003](0003-one-process-two-modules.md)) in a container limited to 4 GB ([ADR-0002](0002-linux-image-delivered-with-compose.md)). Its start loads the world for about 15 s of the 17 s it takes, so the start is mostly application work, not JVM warm-up.

## Decision

1. **G1 is the collector**, set explicitly with `-XX:+UseG1GC` in the default `L2JFREE_JAVA_OPTS`, because without it the JVM picks the serial collector when the container is limited to one CPU. An operator can still replace the options.
2. **The image carries no AOT cache.** The training run needs a PostgreSQL inside the image build, and the cache is valid only for one JDK build and one class path.
3. **The start is measured by a repeatable job** (`deploy/measure/startup.py`, run with `workflow_dispatch`): G1 and ZGC, each with and without the cache, on the same image. The server reports the time from the start of the JVM to the open ports, by step, in its ready line.

## Evidence

Run 37567781550 on a GitHub-hosted `ubuntu-24.04` runner, three restarts per configuration, details in [the report](../reports/startup-measurement.md):

| Configuration | Restart, median | Resident memory |
|---|---:|---:|
| G1 | 16.9 s | 1.2 GiB |
| ZGC | 19.6 s | 2.4 GiB |
| G1 with the AOT cache | 16.7 s | 1.2 GiB |
| ZGC with the AOT cache | 18.7 s | 2.1 GiB |

ZGC starts 16 % slower and holds twice the memory. The AOT cache saves 0.2 s with G1 and 0.9 s with ZGC, at most 5 %, and it was within the noise of the runner in the earlier runs.

## Alternatives considered

- **ZGC without the cache.** It would help if pauses of G1 hurt play, but no such pause is shown, and the heap of 3 GB leaves ZGC little room in 4 GB. It stays available through `L2JFREE_JAVA_OPTS`.
- **G1 with the AOT cache.** It gains less than the noise and adds a build step with a database inside it.
- **A native image.** HotSpot suits a long-running server with scripts compiled at run time.

## Consequences

- The default start takes about 17 s on the measuring runner, and the image build stays simple.
- Nothing measures pause time under player load yet. The load test of the release should record the pauses of G1, and ZGC returns if they exceed what play tolerates.
- The first three runs of the measurement showed restarts 8 s slower than starts in a new container. The cause was the measurement: it read the whole container log at every poll, and the log grew with every start. The script now reads only the end of the log since the start, and all starts take the same time. The record keeps this because the same mistake would hide a real difference in a later measurement.

## Revisit when

A load test shows pauses that hurt play, the start grows well beyond 30 s, or a JDK release makes the AOT cache independent of the class path.

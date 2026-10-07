# Report: start-up measurement

Measured on 2026-10-07 for [ADR-0012](../adr/0012-garbage-collector-and-aot-cache.md). The job `startup` of the workflow `build` starts the image under four JVM configurations on a GitHub-hosted `ubuntu-24.04` runner and tabulates what the server reports in its ready line. It runs on request (`workflow_dispatch`) and never fails the pipeline.

## Method

- The same image, a PostgreSQL 18 container, a heap of `-Xms1g -Xmx3g`, and a limit of 4 GB for the server.
- The time is the one the server logs, counted from the start of the JVM to the moment both ports are open, so Docker and the health check interval are not in it.
- The first start loads the catalog into the empty database (cold). Without a cache that start is reported. With a cache it is the training run that writes the cache when the JVM exits, and it is not reported.
- Then the server runs in a new container on the loaded database, and restarts three times.
- Resident memory is read from the server process ten seconds after the ports open.

## Result

Run 37567781550, three restarts per configuration:

| Configuration | Cold start | New container | Restart, median | Restart, range | Before main | World | Resident memory |
|---|---:|---:|---:|---:|---:|---:|---:|
| G1 | 17.7 s | 17.4 s | 16.9 s | 16.2 - 17.3 s | 0.5 s | 15.5 s | 1179 MiB |
| ZGC | 21.5 s | 20.4 s | 19.6 s | 19.5 - 19.7 s | 0.8 s | 17.8 s | 2419 MiB |
| G1 with AOT cache | - | 17.0 s | 16.7 s | 16.4 - 17.9 s | 0.5 s | 15.4 s | 1243 MiB |
| ZGC with AOT cache | - | 19.1 s | 18.7 s | 18.4 - 18.8 s | 0.9 s | 17.0 s | 2190 MiB |

The same stop followed by a restart, by a start after 30 s, and by a new container took 17.1 s, 17.6 s, and 17.3 s.

## What the earlier runs showed and why they are not used

Three runs before this one gave the same order of the four configurations but restarts 8 s slower than new containers (for G1, 25 s against 17 s), growing with every restart. The script read the whole container log at every poll, the log grew with every start, and the reading slowed the server it measured. Reading only the end of the log removed the difference. The comparison of the collectors and of the cache held in every run: ZGC was 15 to 16 % slower and held twice the memory, and the cache gained between 0 and 2 s.

## Limits

One runner type and one image. The spread inside a configuration is about one second, so differences below one second are not evidence. Pause time under player load is not measured here.

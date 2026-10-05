# 0003. One process with a login module and a world module

- Status: Accepted
- Date: 2026-10-06

## Context

The Lineage II client opens two connections: first to the login port, then to the world port. That is a property of the client protocol and does not require two programs. The 2.x line runs LoginServer and GameServer as two processes joined by an internal socket protocol. For one world on one host that socket connects a process to itself. The two processes also mean two heaps, two collectors, two start-up sequences, and two deliveries that an operator can mix up.

## Decision

Platform 3.0 is one process with two listening ports and two modules. The `login` module owns the login port, the accounts, and session keys. The `world` module owns the world port, the simulation, and the world data. A small `contract` module holds the shared types: session key, admission, and world status. Admission is a Java call inside the process. The internal login-to-world socket and its registration protocol are removed. The client still sees two addresses.

`world` does not read passwords and does not write the `login` schema. `login` does not read the catalog, characters, or world packets. Architecture rules in the test suite fail the build when a dependency crosses those lines.

## Alternatives considered

- Keep two processes. It preserves isolation, but it keeps a socket protocol, duplicate deliveries, and a start-up order for a topology with one world.
- Split the world into services. The world is stateful and latency-sensitive, and remote calls in hot paths add failure modes without a need.

## Consequences

- One heap and one collector are shared. A world deadlock is visible in a single flight recording.
- A restart of the process ends both login and play, which already happens in practice.
- A second world on the same login is outside this platform. If it is needed later, the admission interface gains a socket adapter and the second world runs as another process of the same image.

## Revisit when

A second world must share one login.

# Architecture

Five views of Platform 3.0, from the outside in. The decisions behind them are in the [decision records](adr/README.md). Views marked **target** show where the platform is going. Views marked **today** show what runs now.

## 1. Context (target)

Who and what the platform talks to.

```mermaid
flowchart LR
    player(["🎮 Player<br/>Lineage II client, protocol 83"])
    operator(["🧑‍💻 Operator"])
    subgraph platform["L2JFree Platform 3.0"]
        server["🖥️ Server<br/>login and world in one process"]
        db[("🗄️ PostgreSQL 18")]
        server --> db
    end
    player -- "2106 login<br/>7777 world" --> server
    operator -- "docker compose" --> platform
    ci["⚙️ GitHub Actions"] -- "builds, signs, publishes" --> registry["📦 GHCR<br/>edge and sha images"]
    registry -. "pulled by compose" .-> platform

    classDef person fill:#ddf4ff,stroke:#0969da,color:#0a3069
    classDef system fill:#dafbe1,stroke:#1a7f37,color:#0f5323
    classDef external fill:#f6f8fa,stroke:#8c959f,color:#24292f
    class player,operator person
    class server,db system
    class ci,registry external
```

## 2. Containers (target)

What runs in the compose stack and what it exposes.

```mermaid
flowchart TB
    subgraph host["Docker Desktop · Colima · Docker Engine"]
        subgraph stack["compose project l2jfree"]
            subgraph serverc["server · Arch Linux amd64 · Temurin 25"]
                login["login module"]
                world["world module"]
                contract["contract module"]
                login --> contract
                world --> contract
            end
            pg[("postgres · official image, 18 line")]
            vol1[("secrets volume")]
            vol2[("data volume")]
            serverc --> pg
            pg --- vol2
            serverc -. "reads" .-> vol1
        end
    end
    clients(["🎮 Clients"]) -- "2106" --> login
    clients -- "7777" --> world

    classDef mod fill:#dafbe1,stroke:#1a7f37,color:#0f5323
    classDef store fill:#fff8c5,stroke:#bf8700,color:#4d2d00
    class login,world,contract mod
    class pg,vol1,vol2 store
```

## 3. Walking skeleton (today)

The image that the pipeline builds and publishes now. The login module runs on PostgreSQL 18. The world and the single process arrive in milestone M2.

```mermaid
flowchart TB
    subgraph stack["compose project l2jfree"]
        secrets["secrets<br/>one-shot, creates the password"]
        db[("db · PostgreSQL 18<br/>role and schema per module")]
        login["login · LoginServer<br/>Arch Linux amd64 · Temurin 25"]
        secrets --> db
        db --> login
    end
    clients(["🎮 Clients"]) -- "2106" --> login
    clients -. "7777 published,<br/>no listener yet" .-> login

    classDef today fill:#ddf4ff,stroke:#0969da,color:#0a3069
    class login,db,secrets today
```

## 4. Delivery pipeline (today)

How a change becomes an image.

```mermaid
flowchart LR
    pr["Pull request"] --> build["build<br/>Maven, tests, SBOM, scan"]
    build --> dist[("verified<br/>distributions")]
    dist --> image["image<br/>build, verify, compose up"]
    image --> probe["probe<br/>first login packet"]
    probe --> gate{"merge to main?"}
    gate -- "no" --> done["checks green"]
    gate -- "yes" --> publish["publish edge and sha-*<br/>sign · SBOM · provenance"]
    publish --> ghcr[("GHCR, private<br/>until the release")]

    classDef step fill:#ddf4ff,stroke:#0969da,color:#0a3069
    classDef store fill:#fff8c5,stroke:#bf8700,color:#4d2d00
    class pr,build,image,probe,publish,done step
    class dist,ghcr store
```

## 5. Login and admission (target)

In the target, admission is a Java call inside one process. The internal socket between login and world of the 2.x line is removed ([ADR-0003](adr/0003-one-process-two-modules.md)).

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant L as login module
    participant K as contract
    participant W as world module
    C->>L: connect to 2106
    L-->>C: first packet
    C->>L: credentials
    L->>L: check the account
    L->>K: admit(account, session key)
    L-->>C: world list and session key
    C->>W: connect to 7777 with the key
    W->>K: verify(session key)
    K-->>W: accepted
    W-->>C: enter the world
```

## Reading the views

| View | Question it answers |
|---|---|
| Context | Who uses the platform and what it depends on |
| Containers | What runs, what it exposes, and where state lives |
| Walking skeleton | What is really delivered today |
| Delivery pipeline | How a change reaches a published image |
| Login and admission | How a player gets into the world |

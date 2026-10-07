# Stand profile

Settings for a stand that an external simulator drives and observes: a client
simulator that logs in, plays, and reads back what the server saved. The shipped
configuration and the defaults of the code stay unchanged. The profile is a
compose overlay plus three files of the operator directory
([ADR-0011](../../../docs/adr/0011-configuration-model.md)).

```sh
cd deploy
docker compose -f compose.yaml -f stand/simulator/compose.yaml up -d
```

## What the profile sets

| Setting | Stand value | Shipped | Where |
|---|---|---|---|
| `LogItems` | `True` | `False` | `config/options.properties` |
| `AutoLootAdena` | `False` | `True` | `config/altsettings.properties` |
| `AutoLoot` | `True` (as shipped) | `True` | `config/altsettings.properties` |
| `PacketFinal` | `True` (as shipped) | `True` | `config/server.properties` |
| `StrictFinal` | `False` (as shipped) | `False` | `config/server.properties` |
| `MinProtocolRevision` | `12` (as shipped) | `12` | `config/server.properties` |
| `MaxProtocolRevision` | `87` (as shipped) | `87` | `config/server.properties` |
| JVM time zone | `-Duser.timezone=UTC` | system zone | `L2JFREE_JAVA_OPTS` in `compose.yaml` |
| Item log | a new file per start | appended | `L2JFREE_ITEM_LOG=rotate` |
| Database reader | role `simulator_ro` | none | `readonly-role.sh` |

The protocol keys are stated although they keep the shipped values: the
simulator reads them as facts of the stand, and without the revision keys the code
defaults (`MinProtocolRevision` 694, `MaxProtocolRevision` 709 in `Config.java`)
would reject protocol 83.

The files are the operator directory of the server: a key that is not in them
keeps its default. Starting without the overlay returns to the shipped values.

### Time zone

Log time stamps have neither a year nor a zone (`dd MMM HH:mm:ss,SSS`), so the
stand runs the JVM in UTC. This changes nothing in the log format. Game schedules
that follow the local clock (sieges, daily resets) follow UTC on the stand.

### Item log

`LogItems = True` writes every item event to `log/item/item.log`. The log grows fast.
With `L2JFREE_ITEM_LOG=rotate` each start moves the item log files of the previous run
to `log/item/runs/<UTC time>/` and keeps the last ten runs, so one run is one server
start:

```sh
docker compose -f compose.yaml -f stand/simulator/compose.yaml restart server
```

Logback still rolls `item.log` within a run (10 MB per file).

### Read-only database role

`simulator_ro` may connect to the `l2jfree` database and read five columns of
`world.player`, nothing else: no other column or table, no `login` schema, no
writes, no DDL, and its sessions start read-only. The server stores a player when the
player leaves, so `exp` and `sp` read after that are the saved values.

Create the role once the server has started and migrated the world schema, and again
to change the password. The password is handed to `psql` through the environment; it is
not stored in the repository:

```sh
read -rs -p 'simulator_ro password: ' STAND_DB_PASSWORD; echo
export STAND_DB_PASSWORD
docker compose -f compose.yaml -f stand/simulator/compose.yaml \
  exec -e STAND_DB_PASSWORD db /opt/stand/readonly-role.sh
unset STAND_DB_PASSWORD
```

`STAND_DB_PASSWORD_FILE` instead names a file inside the container that holds the
password. The overlay publishes PostgreSQL on `127.0.0.1:5432`; set `STAND_DB_BIND` to
the address the simulator connects from. The simulator connects with:

```
jdbc:postgresql://<stand host>:5432/l2jfree   user simulator_ro
```

If a simulator expects another role name, change it in `readonly-role.sql` and in the
test below.

## Tests

`StandReadOnlyRoleTest` (`l2jfree-core`) runs `readonly-role.sql` on a migrated world
database and checks what the role may and may not do.

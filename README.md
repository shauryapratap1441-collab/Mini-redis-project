# Mini Redis    🔗 **[Try it live](https://mini-redis-1nst.onrender.com)**

A from-scratch, in-memory key-value store in Java, built to mirror the core
mechanics of real Redis: a plaintext TCP protocol, multiple data types,
concurrent clients, disk persistence, and key expiry.

Built as a learning project to understand how a database like Redis actually
works under the hood — not using any existing Redis library, just raw
`java.net` sockets and `java.util` collections.

## Features

- **Strings** — `SET`, `GET`
- **Lists** — `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`
- **Hashes** — `HSET`, `HGET`, `HDEL`, `HGETALL`
- **Key expiry** — `EXPIRE`, `TTL`
- **Key management** — `DEL`, `EXISTS`
- **Concurrent clients** — thread pool, telnet-compatible TCP server
- **Browser demo** — a hand-built HTTP + WebSocket server (no libraries) serving a live terminal UI
- **Persistence** — periodic snapshots to disk, reloaded on startup
- **Graceful shutdown** — saves a final snapshot on `Ctrl+C` / `SIGTERM`

## Architecture

```
Client (telnet / socket) 
      │  plaintext commands over TCP
      ▼
   Server.java        — accepts connections, hands each off to a thread pool
      │
      ▼
CommandParser.java     — parses a line into a command + args, dispatches it
      │
      ▼
   Storage.java        — the actual data structures + all the logic
      │
      ▼
   dump.rdb            — periodic snapshot (Java serialization)
```

The layering mirrors real Redis at a small scale: a network layer that knows
nothing about data types, a command layer that knows nothing about sockets,
and a storage layer that knows nothing about either.

## Design decisions

A few choices were made deliberately to keep the project scoped and
explainable, rather than by accident:

- **Coarse-grained locking.** Every public `Storage` method is `synchronized`
  on the single `Storage` instance, rather than locking per-key. This avoids
  race conditions in check-then-act patterns (e.g. two clients `LPUSH`ing to
  a new key at once) at the cost of some throughput under heavy concurrent
  load. For a learning project, simplicity won out over performance.
- **Lazy expiry.** TTLs are checked on access (`isExpired()`), not with a
  background sweep thread — the same approach Redis itself uses for most
  keys. `SET` always clears a key's existing TTL, matching Redis's default
  behavior (no `KEEPTTL` support here).
- **`WRONGTYPE` errors.** Calling a String command on a List key (or vice
  versa) returns an error instead of silently succeeding, matching real
  Redis semantics.
- **Snapshot persistence over an append-only log.** Simpler to implement
  correctly than AOF, at the cost of losing up to 30 seconds of writes on a
  hard crash (mitigated by the graceful-shutdown save).
- **Thread pool over one-thread-per-client.** `ExecutorService` avoids
  unbounded thread creation if many clients connect at once.

## Try it in your browser

The same Java process that runs the TCP server also runs a small hand-built
HTTP + WebSocket server (`WebServer.java`) that serves a terminal-style web
page and bridges every command straight into the same `CommandParser` used
by telnet clients. No separate frontend server, no WebSocket library — just
`java.net.Socket`s and a manual RFC 6455 handshake/frame implementation,
in keeping with the rest of the project.

Locally:

```bash
mvn compile exec:java -Dexec.mainClass="com.Shaurya.miniredis.Main"
```

Then open `http://localhost:8080` in a browser.

## Deploying

The app is a single Docker container exposing one HTTP(S)/WebSocket port
(read from the `PORT` env var, so it works with any platform that injects
one) plus the raw TCP port 6379 for telnet, which most free-tier platforms
won't expose publicly — that's fine, the browser demo is the primary way
for others to try it live.

1. Push this repo to GitHub.
2. On [Render](https://render.com) (or Railway/Fly.io), create a new **Web
   Service** from the repo, environment: **Docker**.
3. No extra config needed — Render auto-detects the `Dockerfile`, injects
   `PORT`, and provisions a public HTTPS URL with WebSocket support.
4. Once deployed, visit the assigned URL — that's your live demo link for
   LinkedIn and your resume.

## Running it

Requires JDK 24 and Maven.

```bash
mvn compile exec:java -Dexec.mainClass="com.Shaurya.miniredis.Main"
```

The server listens on port `6379` (Redis's default port). Connect with
telnet or `nc`:

```bash
telnet localhost 6379
SET name gemini
GET name
LPUSH fruits apple
LPUSH fruits banana
LRANGE fruits 0 10
EXPIRE name 10
TTL name
```

## Example session

```
> SET user:1 alice
OK
> GET user:1
alice
> EXPIRE user:1 5
1
> TTL user:1
5
... wait 6 seconds ...
> GET user:1
(nil)
> HSET user:2 name bob
OK
> HSET user:2 age 30
OK
> HGETALL user:2
{name=bob, age=30}
> DEL user:2
1
> EXISTS user:2
0
```

## Command reference

| Command | Arguments | Description |
|---|---|---|
| `SET` | `key value` | Set a string value, clearing any TTL |
| `GET` | `key` | Get a string value |
| `LPUSH` / `RPUSH` | `key value` | Push to the front/back of a list |
| `LPOP` / `RPOP` | `key` | Pop from the front/back of a list |
| `LRANGE` | `key start end` | Get a range of a list (non-negative indices only) |
| `HSET` | `key field value` | Set a field in a hash |
| `HGET` | `key field` | Get a field from a hash |
| `HDEL` | `key field` | Delete a field from a hash |
| `HGETALL` | `key` | Get all fields/values in a hash |
| `DEL` | `key` | Delete a key of any type |
| `EXISTS` | `key` | Check if a key exists |
| `EXPIRE` | `key seconds` | Set a TTL on an existing key |
| `TTL` | `key` | Get remaining TTL (`-1` = no TTL, `-2` = no key) |

## Known limitations

These were deliberate scope cuts, not oversights:

- `LRANGE` supports non-negative indices only (real Redis supports negative
  indices like `-1` for "last element").
- No `KEEPTTL` option on `SET` — any `SET` clears an existing TTL.
- Snapshotting uses Java's built-in serialization rather than a
  Redis-compatible RDB/AOF format — this store isn't wire-compatible with
  real Redis clients, only with its own plaintext protocol over telnet.
- No authentication or encryption — not intended for exposure on the open
  internet without a layer in front of it.

## Tests

Unit tests for `Storage` live in `src/test/java`. Run them with:

```bash
mvn test
```

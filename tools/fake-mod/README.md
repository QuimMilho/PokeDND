# fake-mod — a stand-in for the Minecraft mod

A small Node program (no dependencies, Node 22 or later) that speaks the **mod protocol** (spec chapters 14.3, 18.2, 18.3, 18.5). The API and the app can be built and tested with it before the real mod exists, and the end-to-end tests use it (phases 40, 41, 59; `App/e2e/fake-mod.ts` connects it from a Playwright test and reads its message log).

**Protocol version spoken: `1`** (`PROTOCOL_VERSION` in `protocol.mjs`). It is the `v` of every message and the `protocolVersion` of `hello` / `hello.ack`. If the API answers with another version, the fake mod stops and says so:

```
The API speaks protocol version 2 (hello.ack); this fake mod speaks version 1. Update the fake mod (tools/fake-mod) or the API so that both use the same protocol version.
```

(exit code 3).

## Run it

```sh
# interactive: prints every message and what the mod would show on screen, until Ctrl-C
node tools/fake-mod/fake-mod.mjs --url ws://localhost:8080/ws/mod --token <server token>

# scripted: runs a JSON scenario and exits (0 = all steps passed)
node tools/fake-mod/fake-mod.mjs --scenario tools/fake-mod/scenarios/lobby-and-walk.json --url … --token …
```

`--url` and `--token` can also come from `FAKE_MOD_URL` and `FAKE_MOD_TOKEN`, or from the scenario file. The token is the one shown once when a server is registered; it is sent as `Authorization: Bearer <token>`.

Options: `--walk-delay <ms>` (default 1500), `--skin-delay <ms>` (default 200), `--block <trainer ids>` (their walks end in `walk.blocked`), `--fail-skin <trainer ids>`, `--quiet`.

## What it does

| The API sends | The fake mod does |
|---|---|
| (connection opens) | sends `hello` (versions, features, state hash), then on `hello.ack` sends `config.manifest` |
| `lobby.open` / `lobby.close` | shows or hides the **join code** (a line `JOIN CODE: …`) |
| `game.state` | remembers it |
| `trainer.spawn` | adds a fake player and answers `trainer.spawned` (id, uuid, position) |
| `trainer.update`, `trainer.remove` | changes or removes the fake player |
| `skin.apply` | after `--skin-delay`, `skin.applied` (or an `error` `SKIN_FAILED` for `--fail-skin`) |
| `party.walk` | `walk.progress` at half the delay, `walk.arrived` after it (or `walk.blocked`) |
| `party.stop` | cancels the walks |
| `party.teleport` | moves at once and sends `walk.arrived` with `teleported: true` |
| `config.patch` | applies the files, answers `config.applied`; `config.missing` is answered with `config.manifest` |
| `resync.request` | sends `hello`, `config.manifest` and a `trainer.spawned` per player again |
| `encounter.start`, `battle.*`, `roll.*`, `camera.*`, `hud.state`, `time.set`, `weather.set`, `link.confirmed` | records them and acknowledges (no screen to show them on) |
| anything else | `error` `UNKNOWN_TYPE` |

Every command is answered with `ack` (or `error`) whose `ref` is the command's `id`. An `id` seen before is ignored. A heartbeat (`ping`) goes out every 20 seconds.

Payloads (the parts the spec leaves open are *Suggested*, Appendix D #86):

| Message | `data` |
|---|---|
| `hello` | `modVersion`, `minecraftVersion`, `protocolVersion`, `features`, `fake: true`, `stateHash`, `players` (the trainer ids it has a fake player for) |
| `hello.ack` | `serverId`, `protocolVersion`, optional `game` (`id`, `state`, `lobbyCode`) |
| `lobby.open` | `code` |
| `trainer.spawn` | `trainerId`, `name`, `position {x,y,z}`, optional `look` |
| `skin.apply` | `trainerId`, and `url` or `username` |
| `party.walk` | `trainerIds` (or `trainerId`), `to {x,y,z}`, optional `walkId`, `nodeId` |
| `party.teleport` | `trainerIds`, `to {x,y,z}` |
| `config.patch` | `version`, `files` (path to text, or `null` to delete) |
| `walk.progress` / `walk.arrived` / `walk.blocked` | `trainerId`, `position`, optional `walkId`, `nodeId`; `fraction` or `reason` |

## Scenarios

A scenario is a JSON file: `url`, `token` (optional), `options` (`walkDelayMs`, `skinDelayMs`, `blockedTrainers`, `failSkins`, `configFiles`) and `steps`:

| Step | Meaning |
|---|---|
| `{ "waitFor": "lobby.open", "data": { "code": "…" }, "dir": "in", "timeoutMs": 5000 }` | wait for a message (`data` is a partial match; `dir` is `in` from the API or `out` from the fake mod). Messages are read in order |
| `{ "send": { "type": "player.joined", "data": { … } } }` | send a message of our own |
| `{ "sleep": 100 }` | wait |
| `{ "expect": { "lobby.code": "…", "players.ana.position.x": 4 } }` | check the fake mod's state by dotted paths |
| `{ "print": "text" }` | write a line |
| `{ "close": true }` | close the connection |

From code: `import { runScenario, FakeModClient } from './tools/fake-mod/client.mjs'`.

## Files

| File | What |
|---|---|
| `protocol.mjs` | version, envelope, ULID, parsing, version check |
| `mod.mjs` | `FakeMod`: one handler per message type; no sockets, no clock (unit tested) |
| `client.mjs` | `FakeModClient` (WebSocket, heartbeat, waiting) and `runScenario` |
| `fake-mod.mjs` | the command line |
| `scenarios/` | examples |

Tests: `node --test tools/test/fake-mod.test.mjs` (handlers, a stub server written in `tools/test/lib/ws-stub.mjs`, the refusal of another protocol version).

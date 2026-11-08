# Mod — PokeDnD for Minecraft

A Fabric mod that shows the game inside Minecraft: Pokémon and trainers in the world, cameras, battle presentation. It never decides rules; it connects to the API and does what the API tells it to. The game can also be played without it, in the web app alone.

Spec: [`project_v2.md`](../project_v2.md), chapters 3, 10, 13.2 and 15.

## Status

A skeleton that builds. The connection to the API (phase 60), the renderer (62 to 64) and the rest of the mod come later; see [`todo.md`](../todo.md).

## Build and run

Needs Java 25. The first build downloads Minecraft and its libraries, which takes a while.

```sh
./gradlew build         # the mod jar ends up in build/libs
./gradlew runClient     # start Minecraft with the mod, for testing
./gradlew runServer     # start a dedicated server with the mod
```

On Windows PowerShell use `.\gradlew` instead of `./gradlew`.

## Layout

| Path | What |
|---|---|
| `src/main/` | Code that runs on both client and server (`PokeDnD`) |
| `src/client/` | Client-only code (`PokeDnDClient`, data generator) |
| `src/main/resources/fabric.mod.json` | Mod description, authors, licence |
| `LICENSE` | MPL-2.0; the build packs it into the jar |
| `run/` | Development world and logs (**ignored by git**) |

Later the build also copies models, textures and sounds from `../resources/` into the jar (generate them first with `node tools/extract.mjs`), and includes Core as a jar-in-jar.

## Licence

Our code is MPL-2.0. The Pokémon art comes from Cobblemon under CC BY-NC 3.0, so a build that contains it is **non-commercial only**. See [`CREDITS.md`](../CREDITS.md).

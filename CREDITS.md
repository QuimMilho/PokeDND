# Credits and licences

PokeDnD is an **unofficial, free, non-commercial fan project**. It is not affiliated with, endorsed by or sponsored by Nintendo, Creatures Inc., GAME FREAK inc., The Pokémon Company, Mojang, Microsoft or the Cobblemon team.

This file is the full list of what we use and under which licence. The short version is in the [README](README.md).

## Our own code

| What | Licence |
|---|---|
| Core, API, App, Mod and tools (everything written for PokeDnD) | [Mozilla Public License 2.0](LICENSE) |

Every source file carries the short notice defined in [CONTRIBUTING.md](CONTRIBUTING.md).

## Pokémon art, sounds and animations — Cobblemon

The Pokémon models, textures, animations, posers' art and sounds come from **Cobblemon** and are the work of **The Cobblemon Team**.

- Cobblemon: <https://cobblemon.com>
- Cobblemon assets repository: <https://gitlab.com/cable-mc/cobblemon-assets>
- Licence: **Creative Commons Attribution-NonCommercial 3.0** (CC BY-NC 3.0) — <https://creativecommons.org/licenses/by-nc/3.0/>

What this means in practice:

- **Non-commercial only.** No ads, no paid features, no paywalled content. Donations need care (see chapter 21.1 of `project_v2.md`).
- **Everything derived from these assets is modified work, and is labelled "modified".** That covers the icons, the card art, the converted resolvers and posers, and the PDF cards. Every exported card PDF says so in its footer.
- The licence file stays next to the models (`resources/.../license`, written by `tools/extract.mjs`).
- **Who made which model is not known to us.** The Cobblemon jar names only "The Cobblemon Team", and neither the jar nor the assets repository has a credits file. So we credit the team as a whole and name no individual artists. If Cobblemon publishes per-artist credits, they will be added here.
- The Cobblemon team has not yet been asked whether this wording is enough. Asking them is part of phase 77.
- This project follows version **3.0** of the licence, as shipped in the Cobblemon jar. (One fan project cites 4.0; we follow the jar.)

## Cobblemon code and data — MPL-2.0

The species data (JSON), the TM and item definitions, the language files, and the posers we ported from Cobblemon's source code are licensed **MPL-2.0** by The Cobblemon Team (<https://cobblemon.com>). Files we change stay MPL-2.0 and keep their notices.

## Pokémon Showdown — MIT

Move, ability, item and species data come from **Pokémon Showdown** (<https://github.com/smogon/pokemon-showdown>), under the **MIT licence**. We use the copy that ships inside the Cobblemon jar. The MIT notice stays with the data snapshot.

## BlueMap — MIT

**BlueMap** by Blue (TBlueF, Lukas Rieger) draws the live world map: <https://github.com/BlueMap-Minecraft/BlueMap>, MIT licence. We do **not** bundle it; server owners install it themselves.

## SkinRestorer — MIT

**SkinRestorer** by Lionarius gives fake players their skins: <https://modrinth.com/mod/skinrestorer> (source: <https://github.com/Suiranoil/SkinRestorer>), MIT licence. We do **not** bundle it; server owners install it themselves.

## Trademarks

Pokémon, Pokémon character names and related marks are trademarks of **Nintendo, Creatures Inc. and GAME FREAK inc.** PokeDnD is unofficial fan content. It has no affiliation with them and is not for sale.

## Minecraft

Minecraft is a trademark of **Mojang Studios / Microsoft**. PokeDnD is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft. We follow the [Minecraft EULA and brand guidelines](https://www.minecraft.net/en-us/eula).

## Fan-content disclaimer

> Fan project · not for sale · art based on Cobblemon models, CC BY-NC 3.0, modified.

# Contributing

PokeDnD is licensed under the [Mozilla Public License 2.0](LICENSE). This page holds the rules every contributor and every phase follows.

## The source-file notice

Every source file we write (Java, JavaScript/TypeScript, SCSS, Gradle, shell, and so on) starts with this notice, as a comment in the language of the file. **Use this exact text:**

```text
This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at https://mozilla.org/MPL/2.0/.
```

Example in Java:

```java
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
```

Example in TypeScript or JavaScript:

```ts
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
```

Rules:

- Files that cannot hold comments (JSON, images, generated files) get no notice. The licence still covers them.
- Files **copied or ported from Cobblemon** keep Cobblemon's own notice and stay MPL-2.0. Do not replace it.
- Generated files (`resources/`, `build/`, `dist/`) never go into git, so they carry no notice.

## Art and data

- Never remove or hide the credit to The Cobblemon Team. See [CREDITS.md](CREDITS.md).
- Anything made from Cobblemon art (icons, card art, converted posers, PDF cards) is **modified work** and must be labelled so.
- Do not invent artist names. If something is unknown, say it is unknown.
- Nothing that earns money: no ads, no paid features, no paywalls (CC BY-NC).

## Working method

See [CLAUDE.md](CLAUDE.md) and [todo.md](todo.md): the work is cut into small phases, each with a prompt in `phases/`. A phase is ticked only after it is added and its tests pass.

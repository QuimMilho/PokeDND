# PokeDnD — how to work in this repository

- The canonical specification is `project_v2.md`. `project.md` is the original brief and must stay unchanged.
- The work is cut into small phases. `todo.md` lists them with checkboxes. To do work: open `todo.md`, take the first unticked phase whose dependencies are ticked, read its file in `phases/` completely, and follow it.
- Tick a phase in `todo.md` **only after it is added and every test in its prompt passes**. Update the progress line.
- **Do not run `git commit`** unless the user asks.
- Ask the user (AskUserQuestion) instead of assuming. Mark your own proposals *Suggested* and list them in Appendix D of `project_v2.md`.
- Write specs and documents in plain, human-readable language (examples, tables, short sentences).
- Generated files (`resources/`, `build/`, `vendor/`, build output) are git-ignored. Rebuild `resources/` with `node tools/extract.mjs`; never point `tools/port-posers.mjs` at `tools/ported-posers/` (it deletes its output folder first).

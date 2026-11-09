# Phase 23 — API: card layout and PDF engine

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | API |
| **Depends on** | Phase 20 (Signature moves for cards); Phase 14 (Importer: learnsets, derived values, bundle); Phase 04 (API skeleton) |
| **Spec** | chapter 9.1, chapter 9.2, chapter 9.5, chapter 9.6, chapter 9.7, chapter 9.8 (in `project_v2.md`) |

## Goal

The card engine: one species in, a print-ready card out; many cards in, a PDF with the pages laid out for printing.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Which PDF library: OpenPDF or Apache PDFBox?
- Which fonts may be bundled?

## Tasks

- [x] Choose the PDF library (OpenPDF or Apache PDFBox, as the spec allows) and ZXing for QR codes; ask the user if unsure.
- [x] Front: art (the 1,024 px card art, falling back to the 128 px icon with a warning), name, Dex number, types, stage badge, HP and stats, four moves with dice and bands, ability, weaknesses, flavour text (language fallback pt_pt, pt_br, en_us), frame colour by type, and the credit footer line.
- [x] Back: QR code and the optional ID text.
- [x] Page layout: card size (default 63x88 mm), paper (A4), bleed 3 mm, gap 2 mm, crop marks; fonts must allow bundling.
- [x] Deterministic output: the same input gives the same bytes (fixed dates and ids).

## Tests

Every test must pass before the phase counts as done.

- [x] Golden-file tests: the PDF text and the page images of a few cards equal stored references (allow a small pixel tolerance).
- [x] The QR codes decode to the expected card ids.
- [x] N cards give the right page count for the chosen paper and size.
- [x] Missing art falls back to the icon with a warning in the result.

## Done when

A set of cards becomes a print-ready PDF, deterministically.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 23** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.

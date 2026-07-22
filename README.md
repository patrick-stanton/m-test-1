# fable-model-as-code

A **model-as-code pipeline**: generates a Cameo Systems Modeler 2026x **SysML 1.7** architecture model from a **YAML** spec validated by a **JSON Schema**, following **MagicGrid (1st edition)** — and serves as the feasibility spike for the in-Cameo model-manipulation product it describes.

Everything for this project lives in this one folder, so it can be moved or removed cleanly later.

## The two aspects (never conflated)

- **Aspect 1 — Product:** an in-Cameo tool for create/read/update/delete + refactor + inspect + export on SysML 1.7/2 models, with undo, a GUI panel and a chat/AI mode. *Not being built yet.*
- **Aspect 2 — Model:** the SysML 1.7 description of Aspect 1, generated from text. **This is the build target.** The generator that builds it is a working micro-instance of Aspect 1's core capability.

Ground truth for scope and constraints: [`docs/Project_Context_and_Decisions.md`](docs/Project_Context_and_Decisions.md). Operating instructions: [`docs/Fable_ClaudeCode_Prompt_FINAL.md`](docs/Fable_ClaudeCode_Prompt_FINAL.md).

## Artifact chain (design-time order is non-negotiable)

```
A1 metamodel (single source of truth)          a1_metamodel/metamodel.yaml (starter kept as seed)
 ├─→ A2 Cameo Profile      (derived — never hand-edited)
 └─→ A3 JSON Schema        (derived — never hand-edited)
      → A4 YAML spec  → A5 generator (Python outside + Groovy/Java in-JVM) → A6 .mdzip
```

## Execution model — self-gating scripts + typed feedback (amended)

Claude Code cannot reach Cameo, and — **amendment to the original plan** —
the operator does not paste logs back either; feedback arrives only **typed
in the operator's own words**. The pipeline is therefore self-gating:

- every script judges its own outcome, shows a PASS/FAIL dialog ending in a
  short **"Tell your assistant: …"** status line the operator can retype;
- every script checks its prerequisites and **refuses to run** if an earlier
  step didn't actually succeed (no silent half-built models — a fatal error
  cancels its session);
- full report files stay on the Cameo machine for local troubleshooting via
  the decision tables in `phase0/RUNBOOK.md`; `push_reports.bat` optionally
  archives them into the repo when git is available.

Checkpoints are declared from the operator's typed status lines (e.g.
"Phase 0: all passed, undo worked"), not from logs.

## Phase status

Per the session goal, Phases 1–2 artifacts were **built ahead** of the Phase 0
run: everything below "artifacts written" still needs its checkpoint
**confirmed on the Cameo machine** (via the operator's typed status line)
before it counts as passed, and every Cameo API call in the Groovy artifacts
is flagged `[UNVERIFIED]` until then.

| Phase | Content | Status |
|---|---|---|
| 0 | Spikes: Open API reachability, v1 namespace, one Block create+undo, headless-or-not | **Passed on Cameo machine** — operator confirmed ALL CHECKS PASSED + Ctrl+Z undo worked (2026-07-22); headless spike optional/skipped ([`phase0/RUNBOOK.md`](phase0/RUNBOOK.md)) |
| 1 | Walking skeleton: A1→A2→A3→A4→A5 end-to-end | Artifacts written (subsumed by Phase 2 set) — awaiting checkpoint |
| 2 | Full contract: extended A1, derived A2/A3, anti-drift gate, negative tests, generator | **Artifacts written & Python side tested** — awaiting in-Cameo checkpoint |
| 3 | Hardening: view/diagram generation, deletion of removed elements, dry-run diff | Not started |
| 4 | Real domain content (needs `PRODUCT_FUNCTION` input — will be requested, not invented) | Demo spec only |
| 5 | Aspect-1 feasibility write-up | Not started |

## Layout

```
docs/           Context, operating prompt, and NOVICE_SETUP.md (full novice deployment guide)
a1_metamodel/   A1 — canonical metamodel (metamodel.yaml = working truth; starter kept as seed)
derived/        GENERATED A2 profile builder + A3 JSON Schema + anti-drift manifest — never hand-edit
a4_specs/       A4 YAML specs (product_architecture.yaml) + negative/ rejection tests
tools/          pipeline.py (derive | validate | plan | all) + A2 Groovy template
generator/      a5_generator.groovy — the in-Cameo generator (reads build/build_plan.json)
tests/          python -m unittest discover -s tests
phase0/         Phase 0 human-relay package: RUNBOOK.md + spike artifacts
build/          (generated, git-ignored) build_plan.json
```

## Quick start

See [`docs/NOVICE_SETUP.md`](docs/NOVICE_SETUP.md) for the from-zero guide. Short version:

```
pip install -r requirements.txt
python tools/pipeline.py all a4_specs/product_architecture.yaml   # derive + validate + plan
python -m unittest discover -s tests                              # negative + positive tests
```
then in Cameo: run `derived/a2_profile_builder.groovy` once, save, run
`generator/a5_generator.groovy`, pick `build/build_plan.json`.

## Hard constraints (see docs for the full list)

1. A1 is the single source of truth; A2/A3 are derived, never hand-edited.
2. v1 API namespace only (`com.nomagic.magicdraw.sysml.*`); Open API pinned to 2026x.
3. No XMI synthesis-and-import — Open API generation only.
4. All model writes are undoable sessions.
5. MagicGrid 1st-edition package structure, verified in-tool before populating.
6. Diagrams are generated views, never hand-crafted.

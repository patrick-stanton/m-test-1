# Phase 0 Runbook — Spikes & Introspection (self-gating, no relay)

**Operator machine:** Windows 11, Cameo Systems Modeler 2026x (Refresh 1), licensed, Open API accessible.
**Time needed:** ~20–30 minutes total.
**Nothing in Phase 0 modifies any real model.** Spike 1 works only inside a throwaway project you create for it (closed without saving at the end); the headless spike never opens a project at all. The only real-world side effect is that the headless boot consumes a license while it runs.

**Execution model:** logs are never pasted anywhere. Every script judges its own outcome, tells you PASS/FAIL in a dialog, and writes a full report file for local troubleshooting. **You decide locally** using the decision tables below. Feedback to the assistant is **typed in your own words** — after each step, a one-liner like *"Phase 0: all passed, undo worked"* or *"Phase 0: failed on SYSML_PROFILE"* (just read the dialog / SUMMARY tags off the screen) is everything needed to steer the next move. If this machine has git configured for this repository, `push_reports.bat` (in the project folder) optionally archives the report files into the repo — useful history, never required.

Phase 0 answers four questions before the real pipeline runs:

| # | Question | Answered by |
|---|---|---|
| 1 | Is the Open Java API reachable from an in-JVM script? | Spike 1, section S1 |
| 2 | Is the SysML **v1** namespace (`com.nomagic.magicdraw.sysml.*`) present? | Spike 1 S2 (+ headless probe) |
| 3 | Can one stereotyped Block be created **and reverted** via the session manager? | Spike 1 S4 + your Ctrl+Z observation |
| 4 | Can generation run **headless**, or must we drive a running instance? | Headless spike (`headless/`) — **optional**; the main pipeline runs in the GUI either way |

A note on question 3 — two **different** revert mechanisms are proven, and they are not the same thing:

- **Session-abort revert** (`cancelSession`): rolls back an *uncommitted* session. Tested automatically (S4a).
- **Post-commit undo** (`closeSession` then **Ctrl+Z**): undo of a *committed* edit — the mechanism the product's "AI edits individually revertible" requirement actually needs. Tested by you, manually (S4b + step 6 below).

---

## Step 1 — GUI spike (Spike 1)

1. Start Cameo Systems Modeler 2026x.
2. **File > New Project > SysML Project**, name `Phase0Probe`, any location. (Must be the SysML template — the spike needs the SysML profile loaded.)
3. **Tools > Macros > Organize Macros… > Add** (or Create):
   - Name: `Phase0Spike1`
   - Macro Language: **Groovy**
   - File: `phase0/spike1_gui_probe_and_block.groovy`
4. Run the macro (**Tools > Macros > Phase0Spike1**).
5. The finishing dialog says **ALL CHECKS PASSED** or **N FAILED check(s)**; the full report is at **`%USERPROFILE%\phase0_report.txt`**.
6. **Manual undo check:** in the containment tree you should now see a Block `Phase0_UndoMe_KeepAfterClose` (and *no* `Phase0_CancelMe_ShouldVanish`). Press **Ctrl+Z once** — the Block should disappear.
7. Close the project **without saving** (it was throwaway).

### Decision table (what each outcome means)

| Observation | Meaning | What to do |
|---|---|---|
| Dialog: ALL CHECKS PASSED, and Ctrl+Z removed the Block | Environment is good; sessions are undoable | **Proceed** to the A2 profile builder (NOVICE_SETUP.md Part 5, step 3) |
| `[FAIL] PROJECT_OPEN` | No project was open | Open/create `Phase0Probe` first, re-run the macro |
| `[FAIL] SYSML_PROFILE` | Project wasn't made from the SysML template | Recreate the project via File > New Project > **SysML Project**, re-run |
| `[FAIL] CLASS SysMLProfile` / `CLASS SessionManager` | The v1 Open API namespace isn't on the classpath — a real environment problem | Stop. Check the Cameo edition/install (SysML plugin present?). The report's S3 introspection section shows what *is* present |
| `[FAIL] CREATE_THEN_CANCEL` or `CREATE_THEN_CLOSE` | An API signature differs on 2026x | Open the report: the failing line shows the exception, and the S3 section lists the **real** method signatures — the fix is usually renaming one call in the script to match what S3 printed |
| Ctrl+Z did **not** remove the Block | Post-commit undo doesn't map to the session as assumed — core assumption broken | Stop before generating into any real model; the generated model would not be cleanly revertible. Everything else still works |
| Macro won't run at all / language missing | Macro not registered as Groovy | Re-check step 3; any way of running a Groovy script in Cameo is fine |

## Step 2 — Headless spike (OPTIONAL — informational)

The GUI path above is the supported route for the whole pipeline; headless only matters for future CI ambitions. Skip freely.

**Prerequisite:** a Java *compiler* (JDK 17+). Cameo may bundle only a runtime; the script looks for a bundled `javac` first, then one on `PATH`, and reports `NO JAVA COMPILER FOUND` if neither exists (that is a finding, not an error — see docs/NOVICE_SETUP.md Part 3 to install one).

1. Edit **one line** in `headless/run_headless_check.bat`: set `MD_HOME` to the real install path (common locations are listed in the file's header comment).
2. Open `cmd`, `cd` into the `headless` folder, run `run_headless_check.bat`.
3. Everything goes to **`phase0_headless_log.txt`, created next to the `.bat`**.

### Decision table

| Log contains | Meaning | What to do |
|---|---|---|
| `PHASE0-HEADLESS: RESULT=SUCCESS` | Headless generation is feasible on this machine | Nothing now — good news for later CI |
| `NO JAVA COMPILER FOUND` | No JDK installed | Optional: install JDK 17+ (NOVICE_SETUP.md Part 3) and re-run |
| Launch errors in section `[4/5]` | The documented launch pattern differs on 2026x | Section `[2/5]` of the same log dumped your install's own example launch scripts — mirror one of those into the `.bat` (same jars/properties), re-run. If it still fails, run the pipeline GUI-only; nothing downstream depends on headless |
| Licensing stall/error | Headless boot couldn't get a license | Run the pipeline GUI-only; revisit if a license for unattended use appears |

## Gate (self-declared, recorded locally)

Phase 0 is passed on this machine when: Spike 1's dialog says ALL CHECKS PASSED **and** Ctrl+Z removed the Block. Record the outcome however you like (the report files are the record; `push_reports.bat` archives them into the repo if git is available). Do not run the A2/A5 scripts against a model you care about until this gate is green — they also self-check, but Phase 0 is the cheap place to find environment problems.

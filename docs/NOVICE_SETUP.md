# Complete Setup & Deployment Guide (for a total novice)

This guide assumes **zero** prior experience with Python, Java, the command
line, or this project. Follow it top to bottom on the **Windows 11 computer
that has Cameo Systems Modeler 2026x installed** (called "the Cameo machine"
below). Every step says what you should see, so you always know whether it
worked.

You will set up two things:

- **Python** — runs the pipeline *outside* Cameo (checks the YAML spec, builds
  the instruction file for Cameo). Nothing it does can touch a model.
- **Java (a JDK)** — only needed for the *optional* headless test in Phase 0.
  The main pipeline scripts run **inside Cameo itself** (as "macros"), which
  already contains its own Java — you do not install anything for them.

---

## Part 1 — Get the project folder onto the computer

1. In a web browser, open the repository page:
   `https://github.com/talismanlabs/git_test` (branch
   `claude/fable-claudecode-standalone-08d6xd`).
2. Click the green **Code** button → **Download ZIP**.
3. Find the downloaded ZIP in your **Downloads** folder. Right-click it →
   **Extract All…** → extract to a simple place, e.g. `C:\mac-pipeline`.
4. After extracting you should have a folder
   `C:\mac-pipeline\...\fable-model-as-code` containing `docs`, `tools`,
   `a1_metamodel`, `a4_specs`, `derived`, `generator`, `phase0`.
   That `fable-model-as-code` folder is "the project folder" from now on.

> If your IT setup blocks GitHub, any way of copying the
> `fable-model-as-code` folder to the machine (USB stick, network share)
> works identically.

## Part 2 — Install Python (one time, ~5 minutes)

1. In a browser, go to **https://www.python.org/downloads/** and click the
   yellow **Download Python 3.x.x** button (any 3.9-or-newer version is fine).
2. Run the downloaded installer. On the FIRST screen, **tick the checkbox
   "Add python.exe to PATH"** at the bottom — this is the single most
   important click in this guide. Then click **Install Now** and finish.
3. Check it worked: press the **Windows key**, type `cmd`, press Enter — a
   black window opens (this is the "Command Prompt"; you'll use it a lot).
   Type exactly:
   ```
   python --version
   ```
   and press Enter. You should see something like `Python 3.12.4`.

   **If you instead see** `'python' is not recognized…`: the PATH box wasn't
   ticked. Re-run the installer, choose **Modify**, and enable
   "Add python to environment variables", or just uninstall and reinstall
   with the box ticked. Then open a NEW cmd window and try again.

4. Install the two small libraries the pipeline needs. In the same window,
   first move into the project folder (adjust the path if you extracted
   elsewhere — type `cd ` then drag the folder from Explorer into the window
   and its path is pasted for you):
   ```
   cd C:\mac-pipeline\fable-model-as-code
   pip install -r requirements.txt
   ```
   Expect a few lines ending in `Successfully installed ...` (or
   "Requirement already satisfied"). If `pip` is not recognized, use
   `python -m pip install -r requirements.txt` instead.

## Part 3 — Install Java, i.e. a JDK (one time, ~5 minutes — only for the Phase 0 headless test)

The Phase 0 headless check compiles one small Java file, which needs a "JDK"
(Java Development Kit). If you are not running the headless test yet, you can
skip this part and come back.

1. In a browser, go to **https://adoptium.net/temurin/releases/** .
2. Pick: Operating System **Windows**, Architecture **x64**, Package Type
   **JDK**, Version **17 (LTS)** or newer, and download the **.msi** installer.
3. Run it. When the installer shows feature options, set **"Set JAVA_HOME
   variable"** and **"Add to PATH"** to "Will be installed on local hard
   drive". Finish the install.
4. Check it worked — open a NEW cmd window and type:
   ```
   javac -version
   ```
   Expect something like `javac 17.0.11`. If it says not recognized, log out
   and back in (or reboot) once, then try again.

## Part 4 — Run the pipeline (every time the spec changes)

All commands run in cmd, from the project folder (`cd C:\mac-pipeline\fable-model-as-code` first).

**One command does everything:**
```
python tools\pipeline.py all a4_specs\product_architecture.yaml
```
Expected output (numbers may differ slightly):
```
Derived A2 + A3 from A1 v0.2.0:
  wrote derived/a3_schema.json
  wrote derived/a2_profile_builder.groovy
  wrote derived/derivation_manifest.json
SPEC VALID: a4_specs\product_architecture.yaml
Build plan written: ...\build\build_plan.json
  43 elements, 41 relationships, 12 MagicGrid cell packages
```
If instead you see `SPEC REJECTED` with a list of problems: the YAML spec has
an error; each line tells you which element and what is wrong. Fix the spec
and run the command again. Rejection is the pipeline doing its job, not a
crash.

## Part 5 — Run the Cameo half (the "macros")

A macro is just a script Cameo runs inside itself. You register it once and
run it from a menu. The pipeline uses this mechanism for everything inside
Cameo, so there is nothing to install.

**How to register any of the project's Groovy scripts as a macro (same 6
clicks every time):**

1. Open Cameo Systems Modeler 2026x.
2. Menu **Tools > Macros > Organize Macros…**
3. Click **Add** (or **Create**).
4. Fill in: **Name** — anything you like; **Macro Language** — `Groovy`;
   **File** — click browse and select the script file.
5. Click **OK** to save.
6. Run it any time via **Tools > Macros > (the name you gave it)**.

(If your menus differ slightly, search Cameo help for "Macro Engine" — any
way of running a Groovy macro is fine.)

**The order matters. For a fresh target project:**

| Step | Script | When | What you should see |
|---|---|---|---|
| 1 | `phase0/spike1_gui_probe_and_block.groovy` | once, first ever run (Phase 0) | dialog "ALL CHECKS PASSED"; then the Ctrl+Z check (see `phase0/RUNBOOK.md`) |
| 2 | `phase0/headless/run_headless_check.bat` — OPTIONAL (not a macro — run from cmd) | once (Phase 0) | console prints the outcome; log next to the .bat |
| 3 | `derived/a2_profile_builder.groovy` | once per target project | dialog "…OK — now SAVE the project" with a "Tell your assistant" line |
| 4 | **File > Save** in Cameo | after step 3 | — |
| 5 | `generator/a5_generator.groovy` | every generation run | a file-picker opens — select `build\build_plan.json`; then a dialog with created/updated counts and a "Tell your assistant" line |
| 6 | **File > Save** in Cameo | after checking the result | the generated model is now your `.mdzip` |

Each script checks its own prerequisites and refuses to run (with a dialog
explaining why) if an earlier step was skipped or failed — the order above
enforces itself.

Step 5 details: after it finishes, look at the containment tree (left panel) —
you should see packages `1 Problem` and `2 Solution` with the MagicGrid cell
sub-packages, filled with stereotyped elements. The whole run is ONE undo
step: **Edit > Undo (Ctrl+Z)** removes everything the run created, in one go.
Re-running with the same plan makes no duplicates; re-running after editing
the YAML (and re-doing Part 4) applies your changes.

**Which files to copy between machines** (if the Python part runs on a
different computer than Cameo): only two files ever cross —
`derived/a2_profile_builder.groovy` (once per project) and
`build/build_plan.json` (every run). `generator/a5_generator.groovy` is
static — copy it once.

## Part 6 — Giving feedback (no pasting, ever)

Nothing is ever pasted back. Every script finishes with a dialog that shows a
short status line after the words **"Tell your assistant:"** — just type that
line (or your own words to the same effect) into the chat. Examples of
perfectly sufficient feedback:

- "Phase 0: all passed, undo worked"
- "Phase 0: failed on SYSML_PROFILE"
- "A2: ok, 17 stereotypes, 2 warnings"
- "A5: 43 created, 0 updated, 41 relationships, 3 problems"
- "headless: success" (or "headless failed, last line says …" from memory)

That is enough to steer every next step, because the scripts also gate
themselves: each one refuses to run if its prerequisites aren't actually in
place, and a failed run cancels its session so the model is never left
half-built. The full report files stay on your machine (in
`C:\Users\<you>\`, names ending `_report.txt`) for local troubleshooting —
each failure's fix is in the decision tables in `phase0/RUNBOOK.md` and the
table below. Optionally, run `push_reports.bat` (in the project folder) to
archive the report files into the repository if this machine has git — never
required.

## Troubleshooting quick table

| Symptom | Cause | Fix |
|---|---|---|
| `'python' is not recognized` | PATH box not ticked at install | Part 2 step 3 |
| `'pip' is not recognized` | same | use `python -m pip …` |
| `Missing dependency 'pyyaml'` | libraries not installed | Part 2 step 4 |
| `SPEC REJECTED: …` | the YAML spec violates the contract | read the listed problems; fix the spec; re-run |
| `derived/a3_schema.json is stale or hand-edited` | someone edited a derived file or changed A1 without re-deriving | run `python tools\pipeline.py derive` (never edit `derived/` by hand) |
| A5 dialog: `Profile … is not in this project` | step 3/4 of Part 5 skipped | run the A2 builder, save, retry |
| Macro runs but nothing appears | wrong project open, or report shows FAIL lines | open the report file; the decision tables in `phase0/RUNBOOK.md` map each FAIL tag to a fix; tell your assistant the FAIL tag in your own words |
| `javac -version` not recognized | JDK not installed / PATH | Part 3 |

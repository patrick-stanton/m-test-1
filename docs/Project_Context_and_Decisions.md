# Project Context & Decisions — Ground Truth
## Model-as-code capability + the product it describes

**Status:** Authoritative. Fable and its subagents must treat this document as the source of truth for scope and constraints. Where this conflicts with any assumption, this wins. Open/deferred items are listed explicitly in §6; do not silently expand scope into them.

**Prepared:** 21 July 2026 · **Stack:** Cameo Systems Modeler 2026x (Refresh 1) · SysML (Systems Modeling Language) 1.7 · Windows 11 · MagicGrid methodology, 1st edition.

---

## 1. The two aspects — never conflate them
- **Aspect 1 — The Product.** A software tool delivered inside Cameo that lets users **build, maintain, refactor, inspect, export from, and delete data in** SysML 1.7 *or* 2 models, across the full suite of systems-engineering activities.
- **Aspect 2 — The Model.** A SysML 1.7 description of Aspect 1, authored in Cameo 2026x, **generated from a text contract** (model-as-code). This is the immediate build target and the feasibility spike for Aspect 1.

**Load-bearing relationship:** the generator that builds Aspect 2 (Python outside Cameo + Groovy/Java inside Cameo's Java Virtual Machine, via the Open Application Programming Interface) is a *working micro-instance of Aspect 1's core capability*. Everything learned building it is Aspect-1 feasibility evidence.

---

## 2. Aspect 1 — the product (locked)
| Topic | Decision | Consequence |
|---|---|---|
| Core function | Full create/read/update/delete + refactor + inspect + export, across the full SE activity suite | Broad, general-purpose model-manipulation tool |
| Access mode | **Read and modify**, with **undo / session reversion** | All writes go through Cameo's undoable session mechanism (session manager); AI edits must be individually revertible |
| SysML support | **Both 1.7 and 2** | Internal **abstraction layer** over the two API namespaces (`com.nomagic.magicdraw.sysml.*` vs `com.dassault_systemes.modeler.sysml.*`); largest single cost driver |
| Front-ends | **Two:** a GUI panel (build views, find elements) **and** a chat/AI mode ("what is this?", conversational build/find) | One service core; two consumers (GUI + AI agent) |
| Delivery | Any mechanism that works in Cameo; **bridge/plugin recommended** | In-JVM plugin for API + GUI; external orchestrator for AI |
| Scale | Iterate toward **teams up to ~200**; near-term = **Docker one-off capabilities** run against users' models | Start container-local; architect the service core to later move to shared hosting |

---

## 3. Security, data & hosting (locked)
| Topic | Decision | Consequence |
|---|---|---|
| Primary backend driver | **Shared multi-user state / collaboration** (also: heavy compute, external/AI calls, centralized storage) | Something authoritative lives off the individual client; interacts with — and must decide against — Cameo's native Teamwork Cloud |
| Data residency | **Must be self-hostable on-prem** | No mandatory external SaaS dependency; stack deploys inside the customer perimeter |
| Regulatory ceiling (near-term) | **Controlled Unclassified Information (CUI) at most** — *not* air-gapped/classified for now | Target National Institute of Standards and Technology (NIST) Special Publication 800-171-style controls; no CUI leaves the accredited boundary |
| AI inference | **In-boundary / pluggable**, swappable or disable-able per deployment | Not forced fully-local yet, but designed so a future air-gapped deployment can run a self-hosted model with zero outbound calls |
| Access control | **Role-based access + audit logging**; project-level isolation optional | Multi-level classification compartments **deferred** |

---

## 4. Aspect 2 — the model & pipeline (locked)
| Topic | Decision | Consequence |
|---|---|---|
| Modeling scope | **Full MagicGrid** — problem black/white-box + full solution decomposition | Metamodel and generator must cover the whole grid (target scope; built incrementally) |
| MagicGrid edition | **1st edition** | Pin generator + any Cameo template to 1st edition; verify package numbering before populating; **Safety pillar deferred** |
| Authoring format | **YAML (YAML Ain't Markup Language)**, validated by **JSON (JavaScript Object Notation) Schema** | Human/AI-friendly source; schema-enforced |
| Direction | **Forward-only** (spec → model); YAML is source of truth | `.mdzip` is a generated artifact; round-trip **deferred** |
| Simulation | **None** — structure & behavior only; design so it can be added | No Cameo Simulation Toolkit dependency now |
| Storage | **Single local `.mdzip` artifact**; team collaborates on **YAML in git** | No Teamwork Cloud for Aspect 2 |

---

## 5. Pipeline, languages & execution (locked)
| Topic | Decision |
|---|---|
| Generator shape | **Python 3 orchestrator** (outside Cameo) + **Groovy/Java in-JVM generator** (inside Cameo, via Open Application Programming Interface); target the **SysML v1 namespace** for Aspect 2 |
| Artifact order | **Design-time:** canonical metamodel (A1) first → derive Cameo Profile (A2) + JSON Schema (A3). **Run-time:** apply Profile first → validate spec → instantiate elements → place in MagicGrid packages → generate views |
| Profile handling | Authored/versioned once from A1; **loaded and applied**, never regenerated per run |
| Interchange | **Never synthesize XMI (XML Metadata Interchange) and import** — generate through the Open Application Programming Interface only |
| Headless | **Prefer headless / continuous-integration; fall back** to driving a running instance |
| Idempotence | Re-running on an updated spec updates the model predictably; include validate-only/dry-run mode |
| Runtime | **Fable 5 (main/orchestrator) + Opus 4.8 high-reasoning subagents** in Claude Code |
| Execution loop | **Human-relay.** Claude Code runs elsewhere and cannot reach Cameo. Human runs artifacts on the Windows 11 Cameo machine and pastes results back. No checkpoint passes without confirmation |
| Reference docs | **None pre-supplied.** Use public sources; when an exact API detail is needed, generate an **introspection spike** for the human to run against their local Cameo (the local install is the API oracle) |

---

## 6. Deferred / out of scope (do not expand into these)
Round-trip sync · executable simulation · MagicGrid Safety pillar · multi-level classification compartments · fully air-gapped/classified deployment · Teamwork Cloud for Aspect 2. Each is a *later* option the architecture should not preclude, but must not build now.

---

## 7. Constraints & traps (immovable)
1. **Single source of truth = A1.** The Profile (A2) and schema (A3) are derived from it; never hand-edit them independently. Editing A2/A3 by hand is the moment the contract silently breaks.
2. **v1 namespace** for Aspect 2 (`com.nomagic.magicdraw.sysml.*`); the product's v2 path uses `com.dassault_systemes.modeler.sysml.*`.
3. **All writes are undoable sessions** (session manager) — non-negotiable given the undo requirement.
4. **Pin the Open Application Programming Interface to 2026x**; record the version so scripts don't silently break on upgrade.
5. **1st-edition package structure** is fixed and must be verified before populating; do not mix editions.
6. **Diagrams are generated views, not hand-crafted** — build the semantic model, generate views by querying (MagicGrid's own principle). Accept auto-layout.

---

## 8. Glossary
- **API** — Application Programming Interface. **Open API here** = Cameo/MagicDraw Open Java API.
- **CRUD** — Create, Read, Update, Delete.
- **CUI** — Controlled Unclassified Information (U.S. government sensitive-but-unclassified category).
- **JVM** — Java Virtual Machine.
- **MBSE** — Model-Based Systems Engineering.
- **MagicGrid** — the tool-native MBSE methodology; a grid of four SysML pillars (Requirements, Behavior, Structure, Parametrics) × domains (problem black/white-box, solution, implementation).
- **SE** — Systems Engineering.
- **SysML** — Systems Modeling Language.
- **XMI** — XML Metadata Interchange.
- **YAML** — YAML Ain't Markup Language.

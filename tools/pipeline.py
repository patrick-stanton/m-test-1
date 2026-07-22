#!/usr/bin/env python3
"""
pipeline.py — the model-as-code pipeline orchestrator (runs OUTSIDE Cameo).

This is the Python half of the A5 generator. It never talks to Cameo directly
(pure Python cannot reach the in-process Open API); instead it produces the
inputs the in-Cameo Groovy half consumes.

Commands
--------
  derive              A1 → derived/a3_schema.json + derived/a2_profile_builder.groovy
                      + derived/derivation_manifest.json (anti-drift hashes)
  derive --check      verify nothing under derived/ was hand-edited and that A1
                      hasn't changed since last derivation (the anti-drift gate)
  validate SPEC.yaml  validate an A4 spec: JSON-Schema pass, then semantic pass
  plan SPEC.yaml      validate, then compile the spec into build/build_plan.json
                      (the file the in-Cameo generator groovy reads)
  all SPEC.yaml       derive + validate + plan in one go

Why a build plan instead of letting Groovy read YAML: the JVM side stays dumb
and dependency-free (groovy.json ships with Cameo; a YAML parser does not),
and every semantic decision (placement, target resolution, stereotype names)
is made — and validated — in exactly one place, here.

Exit codes: 0 = success, 1 = validation/derivation failure, 2 = usage error.
Requires: Python 3.9+, pyyaml, jsonschema  (pip install -r requirements.txt)
"""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.exit("Missing dependency 'pyyaml'. Run:  pip install -r requirements.txt")
try:
    import jsonschema
except ImportError:  # pragma: no cover
    sys.exit("Missing dependency 'jsonschema'. Run:  pip install -r requirements.txt")

ROOT = Path(__file__).resolve().parent.parent
A1_PATH = ROOT / "a1_metamodel" / "metamodel.yaml"
DERIVED_DIR = ROOT / "derived"
BUILD_DIR = ROOT / "build"
SCHEMA_FILE = "a3_schema.json"
PROFILE_FILE = "a2_profile_builder.groovy"
MANIFEST_FILE = "derivation_manifest.json"

ID_PATTERN = r"^[A-Za-z][A-Za-z0-9_\-]*$"

# SysML base → (UML metaclass to extend, SysML stereotype to specialize or None)
# The stereotype-specialization is what makes e.g. a ServiceComponent also a
# real SysML Block in Cameo's eyes (validation, compartments, BDD support).
SYSML_BASES = {
    "Block":           ("Class",    "Block"),
    "Requirement":     ("Class",    "Requirement"),
    "ConstraintBlock": ("Class",    "ConstraintBlock"),
    "UseCase":         ("UseCase",  None),
    "Activity":        ("Activity", None),
}

SPEC_ID_TAG = "mc_spec_id"   # auto-added identity tag; makes re-runs idempotent


class PipelineError(Exception):
    """A user-facing pipeline failure (bad A1, bad spec, drift detected)."""


# ---------------------------------------------------------------------------
# A1 loading + sanity checks
# ---------------------------------------------------------------------------

def load_a1(path: Path = A1_PATH) -> dict:
    """Load A1 and fail fast on structural problems, so every later stage can
    trust its shape instead of re-checking."""
    if not path.exists():
        raise PipelineError(f"A1 metamodel not found: {path}")
    a1 = yaml.safe_load(path.read_text(encoding="utf-8"))
    errors = []

    meta = a1.get("metamodel") or {}
    for key in ("name", "version", "sysml_version", "profile_name"):
        if not meta.get(key):
            errors.append(f"metamodel.{key} is missing")
    if meta.get("sysml_version") and not str(meta["sysml_version"]).startswith("1."):
        errors.append("metamodel.sysml_version must be a 1.x version (v1 namespace pin)")

    cells = {c["id"]: c for c in a1.get("magicgrid_cells", [])}
    if len(cells) != len(a1.get("magicgrid_cells", [])):
        errors.append("duplicate magicgrid cell ids")
    for c in a1.get("magicgrid_cells", []):
        if not c.get("package_path"):
            errors.append(f"cell {c.get('id')} has no package_path")

    kinds = {k["kind"] for k in a1.get("relationship_kinds", [])}
    names = set()
    for t in a1.get("types", []):
        n = t.get("name")
        if not n or not re.fullmatch(ID_PATTERN, n):
            errors.append(f"type with bad/missing name: {t!r}")
            continue
        if n in names:
            errors.append(f"duplicate type name: {n}")
        names.add(n)
        if t.get("extends") not in SYSML_BASES:
            errors.append(f"type {n}: extends must be one of {sorted(SYSML_BASES)}, got {t.get('extends')!r}")
        if t.get("magicgrid_cell") not in cells:
            errors.append(f"type {n}: magicgrid_cell {t.get('magicgrid_cell')!r} is not a declared cell")
        for tag, tspec in (t.get("tagged_values") or {}).items():
            if tag == SPEC_ID_TAG:
                errors.append(f"type {n}: tag name {SPEC_ID_TAG} is reserved (auto-generated)")
            try:
                tag_value_schema(tspec)
            except PipelineError as e:
                errors.append(f"type {n}.{tag}: {e}")
        for rel in (t.get("allowed_relationships") or []):
            if rel.get("kind") not in kinds:
                errors.append(f"type {n}: unknown relationship kind {rel.get('kind')!r}")
    # second pass: relationship targets must be declared types
    for t in a1.get("types", []):
        for rel in (t.get("allowed_relationships") or []):
            if rel.get("target") not in names:
                errors.append(f"type {t.get('name')}: relationship target {rel.get('target')!r} is not a declared type")

    if errors:
        raise PipelineError("A1 metamodel is invalid:\n  - " + "\n  - ".join(errors))
    return a1


def tag_value_schema(tspec: str) -> dict:
    """Translate an A1 tagged-value type string into a JSON-Schema fragment."""
    simple = {"string": {"type": "string"},
              "bool": {"type": "boolean"},
              "int": {"type": "integer"},
              "ref": {"type": "string"}}
    if tspec in simple:
        return simple[tspec]
    m = re.fullmatch(r"enum\[([^\]]+)\]", tspec or "")
    if m:
        values = [v.strip() for v in m.group(1).split(",") if v.strip()]
        if not values:
            raise PipelineError(f"empty enum: {tspec!r}")
        return {"enum": values}
    raise PipelineError(f"unsupported tagged-value type: {tspec!r}")


# ---------------------------------------------------------------------------
# A3 — JSON Schema derivation
# ---------------------------------------------------------------------------

def build_schema(a1: dict, a1_sha: str) -> dict:
    """Derive the A4-spec JSON Schema from A1. One $defs entry per type; the
    per-type `const` on `type` gives jsonschema a discriminator so error
    messages point at the right branch."""
    defs = {}
    for t in a1["types"]:
        tags = t.get("tagged_values") or {}
        allowed = t.get("allowed_relationships") or []
        rel_schema: dict
        if allowed:
            rel_schema = {
                "type": "array",
                "items": {
                    "type": "object",
                    "required": ["kind", "target"],
                    "additionalProperties": False,
                    "properties": {
                        "kind": {"enum": sorted({r["kind"] for r in allowed})},
                        "target": {"type": "string", "pattern": ID_PATTERN},
                    },
                },
            }
        else:
            # a type with no allowed relationships may not declare any
            rel_schema = {"type": "array", "maxItems": 0}
        defs[t["name"]] = {
            "type": "object",
            "required": ["id", "type", "name"],
            "additionalProperties": False,
            "properties": {
                "id": {"type": "string", "pattern": ID_PATTERN},
                "type": {"const": t["name"]},
                "name": {"type": "string", "minLength": 1},
                "description": {"type": "string"},
                "tags": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {k: tag_value_schema(v) for k, v in tags.items()},
                },
                "relationships": rel_schema,
            },
        }
    return {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "title": f"A3 spec schema — DERIVED from A1 {a1['metamodel']['name']} "
                 f"v{a1['metamodel']['version']} (sha256 {a1_sha[:12]}…). DO NOT HAND-EDIT.",
        "type": "object",
        "required": ["spec", "elements"],
        "additionalProperties": False,
        "properties": {
            "spec": {
                "type": "object",
                "required": ["name", "metamodel_version"],
                "additionalProperties": False,
                "properties": {
                    "name": {"type": "string", "minLength": 1},
                    "metamodel_version": {"const": a1["metamodel"]["version"]},
                    "description": {"type": "string"},
                },
            },
            "elements": {
                "type": "array",
                "minItems": 1,
                "items": {"oneOf": [{"$ref": f"#/$defs/{n}"} for n in sorted(defs)]},
            },
        },
        "$defs": defs,
    }


# ---------------------------------------------------------------------------
# A2 — Cameo profile-builder derivation (Groovy with embedded JSON payload)
# ---------------------------------------------------------------------------

def build_profile_payload(a1: dict, a1_sha: str) -> dict:
    """Everything the in-Cameo profile builder needs, as plain data."""
    stereotypes = []
    enums = {}   # enum type name -> literals

    for t in a1["types"]:
        metaclass, specializes = SYSML_BASES[t["extends"]]
        tag_entries = [{"name": SPEC_ID_TAG, "kind": "string", "enum_type": None}]
        for tag, tspec in (t.get("tagged_values") or {}).items():
            frag = tag_value_schema(tspec)
            if "enum" in frag:
                ename = f"{t['name']}_{tag}_Kind"
                enums[ename] = frag["enum"]
                tag_entries.append({"name": tag, "kind": "enum", "enum_type": ename})
            else:
                kind = {"string": "string", "boolean": "boolean", "integer": "integer"}[frag["type"]]
                tag_entries.append({"name": tag, "kind": kind, "enum_type": None})
        stereotypes.append({
            "name": t["name"],
            "metaclass": metaclass,
            "specializes_sysml": specializes,
            "tags": tag_entries,
        })

    return {
        "derived_from": f"A1 {a1['metamodel']['name']} v{a1['metamodel']['version']}",
        "a1_sha256": a1_sha,
        "profile_name": a1["metamodel"]["profile_name"],
        "enums": [{"name": k, "literals": v} for k, v in sorted(enums.items())],
        "stereotypes": stereotypes,
    }


def build_profile_groovy(payload: dict) -> str:
    """Wrap the payload in the static Groovy builder template. The template
    lives in a2_profile_builder.template.groovy next to this script so the
    JVM-side logic is reviewable as real Groovy, not a Python string."""
    template = (Path(__file__).parent / "a2_profile_builder.template.groovy").read_text(encoding="utf-8")
    blob = json.dumps(payload, indent=2, ensure_ascii=True)
    # Groovy triple-single-quoted strings still process backslash escapes, so
    # any '\' (including the \uXXXX that ensure_ascii emits for non-ASCII)
    # would silently corrupt the embedded JSON. Fail loudly instead; if such
    # content ever becomes legitimate in A1, switch to base64 embedding.
    if "'''" in blob or "\\" in blob:
        raise PipelineError("payload contains characters unsafe for Groovy embedding "
                            "(quote-run, backslash, or non-ASCII) — use base64 embedding before adding such content to A1")
    return template.replace("@@PAYLOAD@@", blob)


# ---------------------------------------------------------------------------
# derive + anti-drift
# ---------------------------------------------------------------------------

def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _derive_outputs(a1_path: Path = A1_PATH):
    a1_bytes = a1_path.read_bytes()
    a1_sha = _sha(a1_bytes)
    a1 = load_a1(a1_path)
    schema = json.dumps(build_schema(a1, a1_sha), indent=2) + "\n"
    groovy = build_profile_groovy(build_profile_payload(a1, a1_sha))
    outputs = {SCHEMA_FILE: schema.encode("utf-8"), PROFILE_FILE: groovy.encode("utf-8")}
    manifest = {
        "derived_from": str(a1_path.relative_to(ROOT)),
        "a1_sha256": a1_sha,
        "metamodel_version": a1["metamodel"]["version"],
        "outputs": {name: _sha(data) for name, data in outputs.items()},
        "note": "GENERATED — never hand-edit files in derived/. Re-run: python tools/pipeline.py derive",
    }
    return a1, outputs, manifest


def cmd_derive(check_only: bool) -> int:
    a1, outputs, manifest = _derive_outputs()
    if check_only:
        problems = []
        mpath = DERIVED_DIR / MANIFEST_FILE
        if not mpath.exists():
            problems.append("no derivation_manifest.json — run derive first")
        else:
            old = json.loads(mpath.read_text(encoding="utf-8"))
            if old.get("a1_sha256") != manifest["a1_sha256"]:
                problems.append("A1 has changed since last derivation — re-run derive")
            for name, sha in manifest["outputs"].items():
                f = DERIVED_DIR / name
                if not f.exists():
                    problems.append(f"missing derived file: {name}")
                elif _sha(f.read_bytes()) != sha:
                    problems.append(f"HAND-EDIT (or stale) detected: derived/{name} does not match derivation from A1")
        if problems:
            print("ANTI-DRIFT CHECK FAILED:")
            for p in problems:
                print("  -", p)
            return 1
        print("Anti-drift check OK: derived/ matches A1", a1["metamodel"]["version"])
        return 0

    DERIVED_DIR.mkdir(exist_ok=True)
    for name, data in outputs.items():
        (DERIVED_DIR / name).write_bytes(data)
    (DERIVED_DIR / MANIFEST_FILE).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Derived A2 + A3 from A1 v{a1['metamodel']['version']}:")
    for name in (*outputs, MANIFEST_FILE):
        print("  wrote derived/" + name)
    return 0


# ---------------------------------------------------------------------------
# A4 validation (schema pass + semantic pass)
# ---------------------------------------------------------------------------

def validate_spec(spec_path: Path, a1: dict, schema: dict):
    """Return (spec, errors). Schema catches shape problems; the semantic pass
    catches everything a schema cannot see (identity, cross-references)."""
    try:
        spec = yaml.safe_load(spec_path.read_text(encoding="utf-8"))
    except yaml.YAMLError as e:
        return None, [f"not valid YAML: {e}"]
    if not isinstance(spec, dict):
        return None, ["spec file must be a YAML mapping with 'spec' and 'elements'"]

    errors = []
    validator = jsonschema.Draft202012Validator(schema)
    known_types = sorted(t["name"] for t in a1["types"])
    for err in sorted(validator.iter_errors(spec), key=lambda e: list(e.absolute_path)):
        loc = "/".join(str(p) for p in err.absolute_path) or "(root)"
        path = list(err.absolute_path)
        # An element failing the top-level oneOf produces an unreadable
        # "not valid under any of the given schemas" dump. Recover the real
        # cause: pick the branch by the element's `type` discriminator and
        # re-validate against only that branch for field-specific messages.
        if err.validator == "oneOf" and len(path) == 2 and path[0] == "elements" and isinstance(err.instance, dict):
            tname = err.instance.get("type")
            if tname not in known_types:
                errors.append(f"schema: at {loc}: unknown type {tname!r}; valid types: {', '.join(known_types)}")
                continue
            branch = {"$ref": f"#/$defs/{tname}", "$defs": schema["$defs"]}
            sub_errs = list(jsonschema.Draft202012Validator(branch).iter_errors(err.instance))
            for serr in sub_errs:
                sloc = loc + ("/" + "/".join(str(p) for p in serr.absolute_path) if serr.absolute_path else "")
                errors.append(f"schema: at {sloc} (type {tname}): {serr.message}")
            if not sub_errs:  # can't happen, but never swallow an error
                errors.append(f"schema: at {loc}: {err.message}")
        else:
            errors.append(f"schema: at {loc}: {err.message}")
    if errors:
        return spec, errors

    types = {t["name"]: t for t in a1["types"]}
    allowed = {
        t["name"]: {(r["kind"], r["target"]) for r in (t.get("allowed_relationships") or [])}
        for t in a1["types"]
    }
    seen = {}
    for i, el in enumerate(spec["elements"]):
        if el["id"] in seen:
            errors.append(f"semantic: duplicate id '{el['id']}' (elements {seen[el['id']]} and {i})")
        seen[el["id"]] = i
        if el["id"] == "null":
            # 'null' collides with the string form of an unset identity tag in
            # the JVM generator — reserved to keep re-run matching unambiguous
            errors.append(f"semantic: element {i}: id 'null' is reserved")
    by_id = {el["id"]: el for el in spec["elements"]}
    for el in spec["elements"]:
        for rel in el.get("relationships") or []:
            tgt = by_id.get(rel["target"])
            if tgt is None:
                errors.append(f"semantic: {el['id']}: relationship target '{rel['target']}' does not exist in this spec")
                continue
            if (rel["kind"], tgt["type"]) not in allowed[el["type"]]:
                errors.append(
                    f"semantic: {el['id']} ({el['type']}) may not have '{rel['kind']}' → "
                    f"{rel['target']} ({tgt['type']}); allowed: "
                    + (", ".join(sorted(f"{k}→{t}" for k, t in allowed[el["type"]])) or "none")
                )
    _ = types
    return spec, errors


def _load_schema_checked() -> tuple:
    """Load A1 + the derived schema, enforcing the anti-drift gate on EVERY
    derived artifact — not just the schema. The A2 profile builder is what the
    operator copies into Cameo, so a stale or hand-edited copy must be caught
    here too, on the normal validate/plan path, not only by `derive --check`."""
    a1, outputs, manifest = _derive_outputs()
    for name, sha in manifest["outputs"].items():
        disk = DERIVED_DIR / name
        if not disk.exists():
            raise PipelineError(f"derived/{name} missing — run:  python tools/pipeline.py derive")
        if _sha(disk.read_bytes()) != sha:
            raise PipelineError(f"derived/{name} is stale or hand-edited — run derive again (never edit derived/)")
    return a1, json.loads((DERIVED_DIR / SCHEMA_FILE).read_text(encoding="utf-8"))


def cmd_validate(spec_path: Path) -> int:
    a1, schema = _load_schema_checked()
    spec, errors = validate_spec(spec_path, a1, schema)
    if errors:
        print(f"SPEC REJECTED: {spec_path} ({len(errors)} problem(s))")
        for e in errors:
            print("  -", e)
        return 1
    print(f"SPEC VALID: {spec_path} — {len(spec['elements'])} elements")
    return 0


# ---------------------------------------------------------------------------
# build plan compilation
# ---------------------------------------------------------------------------

def build_plan(spec: dict, a1: dict, a1_sha: str) -> dict:
    """Compile a validated spec into the flat, ordered instruction list the
    in-Cameo Groovy generator executes. All resolution happens here."""
    types = {t["name"]: t for t in a1["types"]}
    cells = {c["id"]: c for c in a1["magicgrid_cells"]}
    cell_order = [c["id"] for c in a1["magicgrid_cells"]]

    used_paths = []
    for cid in cell_order:
        if any(types[el["type"]]["magicgrid_cell"] == cid for el in spec["elements"]):
            used_paths.append({"cell": cid, "package_path": cells[cid]["package_path"]})

    elements = []
    order = {cid: i for i, cid in enumerate(cell_order)}
    for el in sorted(spec["elements"], key=lambda e: order[types[e["type"]]["magicgrid_cell"]]):
        t = types[el["type"]]
        metaclass, _ = SYSML_BASES[t["extends"]]
        elements.append({
            "id": el["id"],
            "type": el["type"],
            "stereotype": el["type"],
            "base_metaclass": metaclass,
            "name": el["name"],
            "description": el.get("description", ""),
            "package_path": cells[t["magicgrid_cell"]]["package_path"],
            "tags": el.get("tags") or {},
        })
    relationships = [
        {"kind": rel["kind"], "source": el["id"], "target": rel["target"]}
        for el in spec["elements"]
        for rel in (el.get("relationships") or [])
    ]
    return {
        "plan_version": 1,
        "spec_name": spec["spec"]["name"],
        "metamodel_version": a1["metamodel"]["version"],
        "a1_sha256": a1_sha,
        "profile_name": a1["metamodel"]["profile_name"],
        "spec_id_tag": SPEC_ID_TAG,
        "packages": used_paths,
        "elements": elements,
        "relationships": relationships,
    }


def cmd_plan(spec_path: Path, out: Path | None) -> int:
    a1, schema = _load_schema_checked()
    spec, errors = validate_spec(spec_path, a1, schema)
    if errors:
        print(f"SPEC REJECTED: {spec_path} ({len(errors)} problem(s))")
        for e in errors:
            print("  -", e)
        return 1
    plan = build_plan(spec, a1, _sha(A1_PATH.read_bytes()))
    out = out or (BUILD_DIR / "build_plan.json")
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    print(f"SPEC VALID: {spec_path}")
    print(f"Build plan written: {out}")
    print(f"  {len(plan['elements'])} elements, {len(plan['relationships'])} relationships, "
          f"{len(plan['packages'])} MagicGrid cell packages")
    print("Next: copy build_plan.json to the Cameo machine and run generator/a5_generator.groovy (see docs/NOVICE_SETUP.md)")
    return 0


# ---------------------------------------------------------------------------

def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("derive", help="derive A2 + A3 from A1")
    d.add_argument("--check", action="store_true", help="verify no drift instead of writing")
    v = sub.add_parser("validate", help="validate an A4 spec")
    v.add_argument("spec", type=Path)
    pl = sub.add_parser("plan", help="validate a spec and emit build/build_plan.json")
    pl.add_argument("spec", type=Path)
    pl.add_argument("-o", "--out", type=Path, default=None)
    al = sub.add_parser("all", help="derive + validate + plan")
    al.add_argument("spec", type=Path)
    args = p.parse_args(argv)

    try:
        if args.cmd == "derive":
            return cmd_derive(args.check)
        if args.cmd == "validate":
            return cmd_validate(args.spec)
        if args.cmd == "plan":
            return cmd_plan(args.spec, args.out)
        if args.cmd == "all":
            rc = cmd_derive(False)
            return rc if rc else cmd_plan(args.spec, None)
    except PipelineError as e:
        print(f"ERROR: {e}")
        return 1
    return 2


if __name__ == "__main__":
    sys.exit(main())

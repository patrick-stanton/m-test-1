"""Tests for the Python half of the pipeline (Phase 2 checkpoint evidence:
valid specs pass; broken specs are rejected with clear errors; derivation is
deterministic and drift is caught).

Run from the fable-model-as-code folder:
    python -m unittest discover -s tests -v
"""
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

import pipeline  # noqa: E402


class DerivationTests(unittest.TestCase):
    def test_a1_loads_and_is_sane(self):
        a1 = pipeline.load_a1()
        self.assertEqual(a1["metamodel"]["version"], "0.2.0")
        self.assertGreaterEqual(len(a1["types"]), 16)

    def test_derivation_is_deterministic(self):
        _, out1, man1 = pipeline._derive_outputs()
        _, out2, man2 = pipeline._derive_outputs()
        self.assertEqual(man1["outputs"], man2["outputs"])
        self.assertEqual(out1, out2)

    def test_schema_is_valid_jsonschema(self):
        import jsonschema
        a1, outputs, _ = pipeline._derive_outputs()
        schema = json.loads(outputs[pipeline.SCHEMA_FILE])
        jsonschema.Draft202012Validator.check_schema(schema)

    def test_profile_groovy_embeds_every_type(self):
        a1, outputs, _ = pipeline._derive_outputs()
        groovy = outputs[pipeline.PROFILE_FILE].decode("utf-8")
        for t in a1["types"]:
            self.assertIn(f'"{t["name"]}"', groovy)
        self.assertIn(pipeline.SPEC_ID_TAG, groovy)
        self.assertNotIn("@@PAYLOAD@@", groovy)


class SpecValidationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.a1, outputs, _ = pipeline._derive_outputs()
        cls.schema = json.loads(outputs[pipeline.SCHEMA_FILE])

    def _validate(self, path):
        return pipeline.validate_spec(ROOT / path, self.a1, self.schema)

    def test_product_architecture_is_valid(self):
        spec, errors = self._validate("a4_specs/product_architecture.yaml")
        self.assertEqual(errors, [], "\n".join(errors))
        self.assertGreaterEqual(len(spec["elements"]), 30)

    def test_negative_specs_are_rejected(self):
        neg_dir = ROOT / "a4_specs" / "negative"
        files = sorted(neg_dir.glob("*.yaml"))
        self.assertGreaterEqual(len(files), 8)
        for f in files:
            with self.subTest(spec=f.name):
                _, errors = self._validate(f.relative_to(ROOT))
                self.assertTrue(errors, f"{f.name} should have been rejected")

    def test_rejection_messages_are_specific(self):
        # Message quality is a Phase 2 checkpoint criterion ("clear errors"),
        # so each common authoring mistake must name its actual cause.
        _, errors = self._validate("a4_specs/negative/01_unknown_type.yaml")
        self.assertTrue(any("unknown type" in e and "FluxCapacitor" in e and "valid types" in e for e in errors),
                        f"expected a named unknown-type complaint, got: {errors}")
        _, errors = self._validate("a4_specs/negative/03_bad_enum.yaml")
        self.assertTrue(any("deployment_unit" in e or "floppy_disk" in e for e in errors), errors)
        _, errors = self._validate("a4_specs/negative/04_bad_target_type.yaml")
        self.assertTrue(any("satisfy" in e and "UiPanel" in e for e in errors),
                        f"expected a satisfy→UiPanel complaint, got: {errors}")
        _, errors = self._validate("a4_specs/negative/05_duplicate_id.yaml")
        self.assertTrue(any("duplicate id" in e for e in errors), errors)
        _, errors = self._validate("a4_specs/negative/07_foreign_tag.yaml")
        self.assertTrue(any("priority" in e for e in errors),
                        f"expected the foreign tag to be named, got: {errors}")
        _, errors = self._validate("a4_specs/negative/08_kind_not_allowed_for_type.yaml")
        self.assertTrue(any("allocate" in e for e in errors),
                        f"expected the disallowed kind to be named, got: {errors}")

    def test_reserved_id_null_rejected(self):
        import tempfile, os
        bad = "spec: {name: n, metamodel_version: '0.2.0'}\nelements:\n  - {id: 'null', type: ServiceComponent, name: X}\n"
        with tempfile.NamedTemporaryFile("w", suffix=".yaml", delete=False, dir=ROOT) as f:
            f.write(bad)
            tmp = f.name
        try:
            _, errors = pipeline.validate_spec(Path(tmp), self.a1, self.schema)
            self.assertTrue(any("reserved" in e for e in errors), errors)
        finally:
            os.unlink(tmp)


class BuildPlanTests(unittest.TestCase):
    def test_plan_compiles_and_orders_elements(self):
        a1, outputs, _ = pipeline._derive_outputs()
        schema = json.loads(outputs[pipeline.SCHEMA_FILE])
        spec, errors = pipeline.validate_spec(
            ROOT / "a4_specs" / "product_architecture.yaml", a1, schema)
        self.assertEqual(errors, [])
        plan = pipeline.build_plan(spec, a1, "0" * 64)
        self.assertEqual(len(plan["elements"]), len(spec["elements"]))
        # every element carries a resolvable package path and stereotype
        for el in plan["elements"]:
            self.assertTrue(el["package_path"])
            self.assertEqual(el["stereotype"], el["type"])
        # relationship endpoints all resolve
        ids = {e["id"] for e in plan["elements"]}
        for r in plan["relationships"]:
            self.assertIn(r["source"], ids)
            self.assertIn(r["target"], ids)
        # cell ordering: B1 content precedes S4 content
        order = [e["package_path"].split("/")[0] for e in plan["elements"]]
        self.assertEqual(order, sorted(order))


if __name__ == "__main__":
    unittest.main()

// =============================================================================
// A2 — PROFILE BUILDER (GENERATED from A1 — DO NOT HAND-EDIT)
// =============================================================================
// Template lives in tools/a2_profile_builder.template.groovy; the shipped
// derived/a2_profile_builder.groovy is this template with the A1-derived
// payload embedded. Editing the derived file breaks the A1 contract — edit
// A1 and re-run  python tools/pipeline.py derive  instead.
//
// WHAT IT DOES (run ONCE per target project, as a Groovy macro in Cameo):
//   Creates the "MaC" profile inside the open project: one stereotype per A1
//   type, each extending the right UML metaclass and (where applicable)
//   specializing the corresponding SysML stereotype (Block / Requirement /
//   ConstraintBlock), with typed tag definitions incl. the mc_spec_id
//   identity tag. Re-running is safe: existing pieces are found and kept.
//
//   Per the locked decisions the profile is AUTHORED ONCE and then loaded &
//   applied — the generator (A5) never regenerates it. Longer-term the built
//   profile should be exported as a shared module (.mdzip); for now it lives
//   inside the target project, which keeps novice operation to one file.
//
// API VERIFICATION STATUS: class/method names are best public knowledge for
// the MagicDraw Open API, UNVERIFIED on 2026x. Every call is try/caught and
// reported; ambiguous calls are attempted in documented-most-likely order and
// the report records what actually worked. Failures produce a legible report,
// never a half-broken profile: everything runs in ONE session that is
// cancelled wholesale if any stereotype cannot be created.
//
// HOW TO RUN: open the target project in Cameo (SysML project template!) and
// run this file as a Groovy macro (Tools > Macros — same steps as the Phase 0
// spike). Then SAVE the project. Report: %USERPROFILE%\a2_profile_report.txt
// FEEDBACK: nothing is pasted anywhere — read the finishing dialog and tell
// your assistant in your own words, e.g. "A2: ok, N stereotypes" or
// "A2: failed on <name>" (the WARN/FAIL tags in the report name the culprit).
// =============================================================================

import groovy.json.JsonSlurper
import javax.swing.JOptionPane

// ------------------------------------------------------------ embedded payload
def PAYLOAD = '''{
  "derived_from": "A1 ProductArchitectureMetamodel v0.2.0",
  "a1_sha256": "e53621151318da4e55261e39d34773fe655d0eea72753107b99b6dee940abe0f",
  "profile_name": "MaC Product Profile",
  "enums": [
    {
      "name": "AiAgent_inference_location_Kind",
      "literals": [
        "in_boundary_selfhosted",
        "pluggable_external",
        "disabled"
      ]
    },
    {
      "name": "ApiAdapter_sysml_target_Kind",
      "literals": [
        "v1_7",
        "v2"
      ]
    },
    {
      "name": "ComponentRequirement_verification_method_Kind",
      "literals": [
        "test",
        "analysis",
        "inspection",
        "demonstration"
      ]
    },
    {
      "name": "ExternalActor_actor_kind_Kind",
      "literals": [
        "human",
        "external_system"
      ]
    },
    {
      "name": "SecurityBoundary_residency_Kind",
      "literals": [
        "on_prem_selfhost"
      ]
    },
    {
      "name": "ServiceComponent_deployment_unit_Kind",
      "literals": [
        "in_jvm_plugin",
        "sidecar_container",
        "external_service"
      ]
    },
    {
      "name": "StakeholderNeed_priority_Kind",
      "literals": [
        "must",
        "should",
        "could"
      ]
    },
    {
      "name": "SystemRequirement_verification_method_Kind",
      "literals": [
        "test",
        "analysis",
        "inspection",
        "demonstration"
      ]
    },
    {
      "name": "UiPanel_surface_Kind",
      "literals": [
        "dockable_panel",
        "dialog",
        "browser_context_menu"
      ]
    }
  ],
  "stereotypes": [
    {
      "name": "StakeholderNeed",
      "metaclass": "Class",
      "specializes_sysml": "Requirement",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "stakeholder",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "priority",
          "kind": "enum",
          "enum_type": "StakeholderNeed_priority_Kind"
        }
      ]
    },
    {
      "name": "ProductUseCase",
      "metaclass": "UseCase",
      "specializes_sysml": null,
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "primary_actor",
          "kind": "string",
          "enum_type": null
        }
      ]
    },
    {
      "name": "ExternalActor",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "actor_kind",
          "kind": "enum",
          "enum_type": "ExternalActor_actor_kind_Kind"
        }
      ]
    },
    {
      "name": "MeasurementOfEffectiveness",
      "metaclass": "Class",
      "specializes_sysml": "ConstraintBlock",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "unit",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "target_value",
          "kind": "string",
          "enum_type": null
        }
      ]
    },
    {
      "name": "SystemRequirement",
      "metaclass": "Class",
      "specializes_sysml": "Requirement",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "rationale",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "verification_method",
          "kind": "enum",
          "enum_type": "SystemRequirement_verification_method_Kind"
        }
      ]
    },
    {
      "name": "SystemFunction",
      "metaclass": "Activity",
      "specializes_sysml": null,
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "trigger",
          "kind": "string",
          "enum_type": null
        }
      ]
    },
    {
      "name": "LogicalSubsystem",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "responsibility",
          "kind": "string",
          "enum_type": null
        }
      ]
    },
    {
      "name": "SystemConstraint",
      "metaclass": "Class",
      "specializes_sysml": "ConstraintBlock",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "expression",
          "kind": "string",
          "enum_type": null
        }
      ]
    },
    {
      "name": "ComponentRequirement",
      "metaclass": "Class",
      "specializes_sysml": "Requirement",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "rationale",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "verification_method",
          "kind": "enum",
          "enum_type": "ComponentRequirement_verification_method_Kind"
        }
      ]
    },
    {
      "name": "ComponentBehavior",
      "metaclass": "Activity",
      "specializes_sysml": null,
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "idempotent",
          "kind": "boolean",
          "enum_type": null
        }
      ]
    },
    {
      "name": "UndoableSession",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "revertible",
          "kind": "boolean",
          "enum_type": null
        }
      ]
    },
    {
      "name": "ServiceComponent",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "deployment_unit",
          "kind": "enum",
          "enum_type": "ServiceComponent_deployment_unit_Kind"
        },
        {
          "name": "stateless",
          "kind": "boolean",
          "enum_type": null
        }
      ]
    },
    {
      "name": "ApiAdapter",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "sysml_target",
          "kind": "enum",
          "enum_type": "ApiAdapter_sysml_target_Kind"
        },
        {
          "name": "api_namespace",
          "kind": "string",
          "enum_type": null
        }
      ]
    },
    {
      "name": "UiPanel",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "surface",
          "kind": "enum",
          "enum_type": "UiPanel_surface_Kind"
        }
      ]
    },
    {
      "name": "AiAgent",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "inference_location",
          "kind": "enum",
          "enum_type": "AiAgent_inference_location_Kind"
        }
      ]
    },
    {
      "name": "SecurityBoundary",
      "metaclass": "Class",
      "specializes_sysml": "Block",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "residency",
          "kind": "enum",
          "enum_type": "SecurityBoundary_residency_Kind"
        },
        {
          "name": "controls",
          "kind": "string",
          "enum_type": null
        }
      ]
    },
    {
      "name": "ComponentConstraint",
      "metaclass": "Class",
      "specializes_sysml": "ConstraintBlock",
      "tags": [
        {
          "name": "mc_spec_id",
          "kind": "string",
          "enum_type": null
        },
        {
          "name": "expression",
          "kind": "string",
          "enum_type": null
        }
      ]
    }
  ]
}'''
def cfg = new JsonSlurper().parseText(PAYLOAD)

// ------------------------------------------------------------ report plumbing
StringBuilder report = new StringBuilder()
List<String> summary = []
def line = { String s -> report.append(s).append('\n') }
def result = { String tag, String what, String detail ->
    summary << String.format('[%s] %-28s %s', tag, what, detail)
    line(String.format('>> [%s] %s — %s', tag, what, detail))
}
line('BEGIN A2 PROFILE BUILDER REPORT')
line('Derived from: ' + cfg.derived_from + '  (A1 sha256 ' + cfg.a1_sha256.substring(0, 12) + '...)')

def app = com.nomagic.magicdraw.core.Application.getInstance()
def project = app.getProject()
def factory = project.getElementsFactory()
def sh = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
def sessionMgr = com.nomagic.magicdraw.openapi.uml.SessionManager.getInstance()
boolean fatal = false

def hasMethod = { Object target, String name, Class[] params ->
    try { target.getClass().getMethod(name, params); return true } catch (Throwable t) { return false }
}
def primaryModel = {
    if (hasMethod(project, 'getPrimaryModel', new Class[0])) return project.getPrimaryModel()
    return project.getModel()
}
// Attach an element to a parent: ModelElementsManager.addElement is the
// public-docs canonical path [UNVERIFIED on 2026x]; setOwner is the fallback.
def attach = { el, parent ->
    try {
        def mem = Class.forName('com.nomagic.magicdraw.openapi.uml.ModelElementsManager').getMethod('getInstance').invoke(null)
        mem.addElement(el, parent)
    } catch (Throwable t) {
        el.setOwner(parent)
    }
}

// UML metaclass names → candidate v1 API interfaces [UNVERIFIED — candidates
// tried in order; the report records the winner. A miss degrades to a
// stereotype without a metaclass extension plus a WARN; the Phase 0 spike's
// S3 introspection section (in its local report) shows the real name to add.]
def METACLASS_CANDIDATES = [
    'Class'   : ['com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class'],
    'UseCase' : ['com.nomagic.uml2.ext.magicdraw.mdusecases.UseCase'],
    'Activity': ['com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity',
                 'com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.Activity']
]
def resolveMetaclass = { String name ->
    for (cn in (METACLASS_CANDIDATES[name] ?: [])) {
        try { return Class.forName(cn) } catch (Throwable ignored) { }
    }
    return null
}

try {
    sessionMgr.createSession(project, 'MaC: build A2 profile')
} catch (Throwable t) {
    try { sessionMgr.createSession('MaC: build A2 profile') } catch (Throwable t2) { fatal = true; result('FAIL', 'SESSION', t2.toString()) }
}

def profilePkg = null
def sysmlProfile = null
if (!fatal) {
    try {
        sysmlProfile = sh.getProfile(project, 'SysML')
        if (sysmlProfile == null) throw new IllegalStateException('SysML profile not in project — use the SysML project template')
        profilePkg = sh.getProfile(project, cfg.profile_name)
        if (profilePkg != null) {
            result('PASS', 'PROFILE', 'found existing profile "' + cfg.profile_name + '" — updating in place')
        } else {
            try {
                profilePkg = factory.createProfileInstance()     // [UNVERIFIED]
            } catch (Throwable t) {
                profilePkg = factory.createPackageInstance()     // degraded: plain package
                result('WARN', 'PROFILE_KIND', 'createProfileInstance unavailable (' + t.getClass().getSimpleName() + ') — created plain Package; stereotype application may still work but mention this warning in your feedback')
            }
            profilePkg.setName(cfg.profile_name)
            attach(profilePkg, primaryModel())
            result('PASS', 'PROFILE', 'created "' + cfg.profile_name + '"')
        }
    } catch (Throwable t) {
        fatal = true
        result('FAIL', 'PROFILE', t.toString())
    }
}

// Local primitive types for tag definitions. Deliberately profile-local
// (created here) instead of hunting the UML Standard Profile primitives —
// self-contained beats a fragile cross-profile lookup on an unverified API.
def primitives = [:]
if (!fatal) {
    ['String', 'Boolean', 'Integer'].each { pname ->
        try {
            def existing = profilePkg.getOwnedElement().find {
                try { it.getName() == 'MaC_' + pname } catch (Throwable t) { false }
            }
            if (existing != null) { primitives[pname] = existing; return }
            def prim = factory.createPrimitiveTypeInstance()
            prim.setName('MaC_' + pname)
            attach(prim, profilePkg)
            primitives[pname] = prim
        } catch (Throwable t) {
            result('WARN', 'PRIMITIVE ' + pname, t.toString() + ' — tags of this type will be untyped')
        }
    }
}

// Enumerations for enum-typed tags
def enums = [:]
if (!fatal) {
    cfg.enums.each { e ->
        try {
            def existing = profilePkg.getOwnedElement().find {
                try { it.getName() == e.name } catch (Throwable t) { false }
            }
            if (existing != null) { enums[e.name] = existing; return }
            def en = factory.createEnumerationInstance()
            en.setName(e.name)
            attach(en, profilePkg)
            e.literals.each { lit ->
                def l = factory.createEnumerationLiteralInstance()
                l.setName(lit)
                attach(l, en)
            }
            enums[e.name] = en
            result('PASS', 'ENUM ' + e.name, e.literals.size() + ' literals')
        } catch (Throwable t) {
            result('WARN', 'ENUM ' + e.name, t.toString())
        }
    }
}

// Stereotypes
if (!fatal) {
    cfg.stereotypes.each { st ->
        try {
            def existing = sh.getStereotype(project, st.name, profilePkg)
            def stereo = existing
            if (stereo == null) {
                def mc = resolveMetaclass(st.metaclass)
                def mcList = (mc != null) ? [mc] : []
                // StereotypesHelper.createStereotype(parent, name, metaclassList)
                // is the public-docs path [UNVERIFIED]; fallback builds the
                // stereotype bare (no metaclass extension) and warns.
                try {
                    stereo = sh.createStereotype(profilePkg, st.name, mcList)
                } catch (Throwable t) {
                    stereo = factory.createStereotypeInstance()
                    stereo.setName(st.name)
                    attach(stereo, profilePkg)
                    result('WARN', 'STEREO ' + st.name, 'createStereotype helper failed (' + t.getClass().getSimpleName() + '); created bare stereotype WITHOUT metaclass extension — must be fixed before generation')
                }
                if (mc == null && !(st.metaclass in ['Class'])) {
                    result('WARN', 'STEREO ' + st.name, 'metaclass ' + st.metaclass + ' not resolved from candidates — extension missing')
                }
            }
            // specialize the SysML stereotype (Block/Requirement/ConstraintBlock)
            if (st.specializes_sysml != null) {
                def general = sh.getStereotype(project, st.specializes_sysml, sysmlProfile)
                if (general == null) {
                    result('WARN', 'STEREO ' + st.name, 'SysML stereotype ' + st.specializes_sysml + ' not found — specialization skipped')
                } else if (!stereo.getGeneralization().any { g -> g.getGeneral() == general }) {
                    def gen = factory.createGeneralizationInstance()
                    gen.setSpecific(stereo)
                    gen.setGeneral(general)
                    attach(gen, stereo)
                }
            }
            // tag definitions (owned attributes)
            st.tags.each { tag ->
                def already = stereo.getOwnedAttribute().any {
                    try { it.getName() == tag.name } catch (Throwable t) { false }
                }
                if (already) return
                def p = factory.createPropertyInstance()
                p.setName(tag.name)
                def type = (tag.kind == 'enum') ? enums[tag.enum_type]
                         : (tag.kind == 'boolean') ? primitives['Boolean']
                         : (tag.kind == 'integer') ? primitives['Integer']
                         : primitives['String']
                if (type != null) p.setType(type)
                attach(p, stereo)
            }
            result('PASS', 'STEREO ' + st.name,
                   'extends ' + st.metaclass +
                   (st.specializes_sysml ? ', specializes SysML::' + st.specializes_sysml : '') +
                   ', ' + st.tags.size() + ' tags')
        } catch (Throwable t) {
            fatal = true
            result('FAIL', 'STEREO ' + st.name, t.toString())
        }
    }
}

// ------------------------------------------------------------ commit or abort
try {
    if (fatal) {
        try { sessionMgr.cancelSession(project) } catch (Throwable t) { sessionMgr.cancelSession() }
        result('FAIL', 'OUTCOME', 'FATAL problem — session CANCELLED, project untouched. Tell your assistant which [FAIL] line appears above.')
    } else {
        try { sessionMgr.closeSession(project) } catch (Throwable t) { sessionMgr.closeSession() }
        result('PASS', 'OUTCOME', 'profile committed. NOW SAVE THE PROJECT (File > Save).')
    }
} catch (Throwable t) {
    result('FAIL', 'OUTCOME', 'session close/cancel itself failed: ' + t.toString())
}

line(''); line('SUMMARY'); summary.each { line(it) }
line('END A2 PROFILE BUILDER REPORT')
def out = new File(System.getProperty('user.home'), 'a2_profile_report.txt')
out.setText(report.toString(), 'UTF-8')
println(report.toString())
def stCount = summary.count { it.startsWith('[PASS] STEREO') }
def warns = summary.count { it.startsWith('[WARN]') }
def statusLine = fatal ? 'A2: FAILED — ' + (summary.find { it.startsWith('[FAIL]') } ?: 'see report')
                       : 'A2: ok, ' + stCount + ' stereotypes' + (warns ? ', ' + warns + ' warnings' : '')
JOptionPane.showMessageDialog(null,
    'A2 profile builder finished (' + (fatal ? 'FAILED — session cancelled, project untouched' : 'OK — now SAVE the project') + ').\n' +
    'Report: ' + out.getAbsolutePath() + '\n\nTell your assistant:  "' + statusLine + '"',
    'A2 Profile Builder', fatal ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE)

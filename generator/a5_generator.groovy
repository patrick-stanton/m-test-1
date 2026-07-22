// =============================================================================
// A5 — MODEL GENERATOR (in-Cameo half; the Python half is tools/pipeline.py)
// =============================================================================
// WHAT IT DOES
//   Reads a build_plan.json produced by  python tools/pipeline.py plan  and
//   materializes it in the open Cameo project:
//     1. creates the MagicGrid package tree for every cell the plan uses;
//     2. creates (or updates) one element per plan entry — right metaclass,
//        right package, MaC stereotype applied, tag values set, including the
//        mc_spec_id identity tag;
//     3. creates the relationships (composition/reference parts,
//        generalizations, satisfy/deriveReqt/allocate abstractions, plain
//        dependencies), skipping ones that already exist.
//   Everything runs inside ONE undoable session: a fatal error cancels the
//   session and the project is untouched; success commits it, and a single
//   Ctrl+Z reverts the entire generation run.
//
// IDEMPOTENCE (re-run on an edited spec)
//   Elements are matched by the mc_spec_id tag, not by name — renaming an
//   element in the YAML updates the existing model element instead of
//   duplicating it. Relationship duplicates are detected structurally.
//   (Deletion of elements removed from the spec is a Phase 3 item — the
//   report lists LEFTOVER elements it found but no longer manages.)
//
// PREREQUISITES (in this order)
//   a. The project was created from the SysML project template.
//   b. derived/a2_profile_builder.groovy was run in this project and the
//      project saved (the "MaC" profile must exist — the generator REFUSES
//      to run without it rather than half-building).
//   c. build/build_plan.json copied to this machine.
//
// HOW TO RUN
//   Run this file as a Groovy macro (Tools > Macros, same as Phase 0). A file
//   chooser opens — pick your build_plan.json. When it finishes, read the
//   dialog; full report: %USERPROFILE%\a5_generation_report.txt
//
// API VERIFICATION STATUS: best public knowledge of the MagicDraw Open API,
// UNVERIFIED on 2026x. Uncertain calls are attempted in most-likely order
// inside try/catch, per-element failures are collected (not fatal), and the
// report records exactly what ran. Operator feedback (typed, in their own
// words, from the finishing dialog) refines this file.
// =============================================================================

import groovy.json.JsonSlurper
import javax.swing.JFileChooser
import javax.swing.JOptionPane

// ------------------------------------------------------------ report plumbing
StringBuilder report = new StringBuilder()
List<String> problems = []
int created = 0, updated = 0, relCreated = 0, relSkipped = 0
def line = { String s -> report.append(s).append('\n') }
line('BEGIN A5 GENERATION REPORT')

// ------------------------------------------------------------ pick the plan
def chooser = new JFileChooser()
chooser.setDialogTitle('Select build_plan.json (made by: python tools/pipeline.py plan)')
if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
    JOptionPane.showMessageDialog(null, 'Cancelled — nothing was changed.', 'A5 Generator', JOptionPane.WARNING_MESSAGE)
    return
}
def planFile = chooser.getSelectedFile()
def plan = new JsonSlurper().parse(planFile)
line('Plan: ' + planFile.getAbsolutePath())
line('Spec: ' + plan.spec_name + '  (metamodel v' + plan.metamodel_version + ', A1 sha ' + plan.a1_sha256.substring(0, 12) + '...)')

// ------------------------------------------------------------ environment
def app = com.nomagic.magicdraw.core.Application.getInstance()
def project = app.getProject()
if (project == null) {
    JOptionPane.showMessageDialog(null, 'No project open. Open your target SysML project first.', 'A5 Generator', JOptionPane.ERROR_MESSAGE)
    return
}
def factory = project.getElementsFactory()
def sh = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
def sessionMgr = com.nomagic.magicdraw.openapi.uml.SessionManager.getInstance()

def hasMethod = { Object target, String name, Class[] params ->
    try { target.getClass().getMethod(name, params); return true } catch (Throwable t) { return false }
}
def primaryModel = {
    if (hasMethod(project, 'getPrimaryModel', new Class[0])) return project.getPrimaryModel()
    return project.getModel()
}
def attach = { el, parent ->
    try {
        def mem = Class.forName('com.nomagic.magicdraw.openapi.uml.ModelElementsManager').getMethod('getInstance').invoke(null)
        mem.addElement(el, parent)
    } catch (Throwable t) {
        el.setOwner(parent)
    }
}

// ------------------------------------------------------------ preconditions
def profile = sh.getProfile(project, plan.profile_name)
if (profile == null) {
    JOptionPane.showMessageDialog(null,
        'Profile "' + plan.profile_name + '" is not in this project.\n' +
        'Run derived/a2_profile_builder.groovy first (see docs/NOVICE_SETUP.md), save, then re-run this.',
        'A5 Generator — refusing to run', JOptionPane.ERROR_MESSAGE)
    return
}
def sysml = sh.getProfile(project, 'SysML')
def stereotypes = [:]
def missing = []
plan.elements.collect { it.stereotype }.unique().each { sn ->
    def st = sh.getStereotype(project, sn, profile)
    if (st == null) missing << sn else stereotypes[sn] = st
}
if (!missing.isEmpty()) {
    JOptionPane.showMessageDialog(null,
        'Missing stereotypes in profile: ' + missing.join(', ') + '\nRe-run the A2 profile builder (its report will say why they failed).',
        'A5 Generator — refusing to run', JOptionPane.ERROR_MESSAGE)
    return
}
line('Profile + ' + stereotypes.size() + ' stereotypes resolved OK')

// ------------------------------------------------------------ generation
boolean fatal = false
try {
    try { sessionMgr.createSession(project, 'MaC: generate "' + plan.spec_name + '"') }
    catch (Throwable t) { sessionMgr.createSession('MaC: generate "' + plan.spec_name + '"') }

    // ---- 1. package tree --------------------------------------------------
    def packages = [:]   // package_path -> Package element
    def ensurePackage
    ensurePackage = { String path ->
        if (packages.containsKey(path)) return packages[path]
        def parent = primaryModel()
        def walked = ''
        path.split('/').each { seg ->
            walked = walked ? walked + '/' + seg : seg
            if (packages.containsKey(walked)) { parent = packages[walked]; return }
            def existing = parent.getOwnedElement().find {
                try { it instanceof com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package && it.getName() == seg } catch (Throwable t) { false }
            }
            if (existing == null) {
                existing = factory.createPackageInstance()
                existing.setName(seg)
                attach(existing, parent)
            }
            packages[walked] = existing
            parent = existing
        }
        return packages[path]
    }
    plan.packages.each { p -> ensurePackage(p.package_path); line('package ready: ' + p.package_path + '  (cell ' + p.cell + ')') }

    // ---- 2. elements ------------------------------------------------------
    // Identity = mc_spec_id tag value, searched project-wide within the
    // packages we manage, so moving a type to another cell in A1 relocates
    // cleanly on the NEXT full rebuild (Phase 3) and never duplicates here.
    def byId = [:]       // spec id -> model element
    // Identity lookup must fail LOUDLY, never open: if the (unverified)
    // getStereotypePropertyFirst call is wrong on 2026x, silently returning
    // "not found" would make every re-run duplicate every element. So a read
    // failure on a MANAGED (stereotyped) element aborts the whole run before
    // anything is created; the exception carries the MC_IDENTITY marker so
    // the per-element catch rethrows it instead of swallowing it.
    def findBySpecId = { String specId ->
        for (pkg in packages.values()) {
            for (el in pkg.getOwnedElement()) {
                def st
                try { st = stereotypes.values().find { s -> sh.hasStereotype(el, s) } }
                catch (Throwable t) { continue }   // not stereotype-readable → not ours
                if (st == null) continue
                def raw
                try { raw = sh.getStereotypePropertyFirst(el, st, plan.spec_id_tag) }
                catch (Throwable t) {
                    throw new IllegalStateException('MC_IDENTITY: cannot read ' + plan.spec_id_tag +
                        ' on managed element "' + el.getName() + '" (' + t + ') — aborting before creating duplicates. ' +
                        'Run the Phase 0 spike and check its S3 introspection section for the real getStereotypePropertyFirst signature.')
                }
                if (raw != null && String.valueOf(raw) == specId) return el
            }
        }
        return null
    }
    def createByMetaclass = { String metaclass ->
        switch (metaclass) {
            case 'Class':    return factory.createClassInstance()
            case 'UseCase':  return factory.createUseCaseInstance()
            case 'Activity': return factory.createActivityInstance()
            default: throw new IllegalArgumentException('unsupported metaclass in plan: ' + metaclass)
        }
    }
    def setTag = { el, st, String tag, Object value ->
        // setStereotypePropertyValue with a String usually coerces enum
        // literals by name [UNVERIFIED]; failures are recorded per-tag.
        try { sh.setStereotypePropertyValue(el, st, tag, value) ; return true }
        catch (Throwable t) { problems << ('tag ' + tag + '=' + value + ': ' + t.toString()); return false }
    }

    plan.elements.each { e ->
        try {
            def st = stereotypes[e.stereotype]
            def pkg = ensurePackage(e.package_path)
            def el = findBySpecId(e.id)
            if (el == null) {
                el = createByMetaclass(e.base_metaclass)
                // From here the element exists in the session. If it cannot be
                // stereotyped AND given a readable identity tag it becomes an
                // unidentifiable orphan that the NEXT run duplicates (and the
                // leftover scan never reports, since that scan only sees
                // stereotyped elements). So any failure below aborts the whole
                // run — the symmetric write-side of the findBySpecId read guard.
                try { attach(el, pkg); sh.addStereotype(el, st) }
                catch (Throwable t) {
                    throw new IllegalStateException('MC_CREATE: could not attach/stereotype new element ' + e.id +
                        ' «' + e.stereotype + '» (' + t + ') — aborting so a half-built, unidentifiable element is never committed.')
                }
                boolean idOk = setTag(el, st, plan.spec_id_tag, e.id)
                def back = null
                if (idOk) { try { back = sh.getStereotypePropertyFirst(el, st, plan.spec_id_tag) } catch (Throwable t) { idOk = false } }
                if (!idOk || back == null || String.valueOf(back) != String.valueOf(e.id)) {
                    throw new IllegalStateException('MC_CREATE: identity tag ' + plan.spec_id_tag + '=' + e.id +
                        ' did not persist on the new element (read back ' + back + ') — aborting before the next run duplicates every element. ' +
                        'Check the Phase 0 S3 introspection for the real setStereotypePropertyValue/getStereotypePropertyFirst signatures.')
                }
                created++
                line('created  ' + e.id + '  «' + e.stereotype + '» ' + e.name)
            } else {
                if (el.getOwner() != pkg) { attach(el, pkg); line('moved    ' + e.id + ' into ' + e.package_path) }
                updated++
                line('updated  ' + e.id + '  «' + e.stereotype + '» ' + e.name)
            }
            el.setName(e.name)
            e.tags.each { k, v -> setTag(el, st, k, v) }
            // documentation: Open API exposes it as a comment; best-effort [UNVERIFIED]
            if (e.description) {
                try {
                    def mh = Class.forName('com.nomagic.uml2.ext.jmi.helpers.ModelHelper')
                    mh.getMethod('setComment', Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element'), String).invoke(null, el, e.description)
                } catch (Throwable t) { /* cosmetic — skip silently, Phase 3 */ }
            }
            byId[e.id] = el
        } catch (Throwable t) {
            // identity-read failures must abort the run (see findBySpecId),
            // not degrade to a per-element problem line
            if (t instanceof IllegalStateException && (t.getMessage()?.startsWith('MC_IDENTITY') || t.getMessage()?.startsWith('MC_CREATE'))) throw t
            problems << ('element ' + e.id + ': ' + t.toString())
        }
    }

    // ---- 3. relationships -------------------------------------------------
    // satisfy/derive_reqt/allocate are stereotyped Abstractions in SysML;
    // stereotype names per SysML spec [UNVERIFIED against 2026x profile].
    def DEP_STEREO = [satisfy: 'Satisfy', derive_reqt: 'DeriveReqt', allocate: 'Allocate', dependency: null]

    plan.relationships.each { r ->
        try {
            def src = byId[r.source]; def tgt = byId[r.target]
            if (src == null || tgt == null) { problems << ('rel ' + r.source + '-' + r.kind + '->' + r.target + ': endpoint missing'); return }

            if (r.kind == 'generalization') {
                if (src.getGeneralization().any { it.getGeneral() == tgt }) { relSkipped++; return }
                def g = factory.createGeneralizationInstance()
                g.setSpecific(src); g.setGeneral(tgt); attach(g, src)
                relCreated++
            } else if (r.kind == 'composition' || r.kind == 'reference') {
                def composite = (r.kind == 'composition')
                if (src.getOwnedAttribute().any { it.getType() == tgt }) { relSkipped++; return }
                def p = factory.createPropertyInstance()
                p.setName(tgt.getName().replaceAll(/\W+/, '_').toLowerCase())
                p.setType(tgt)
                attach(p, src)
                try {
                    def agg = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.AggregationKindEnum')
                    p.setAggregation(agg.getField(composite ? 'COMPOSITE' : 'NONE').get(null))
                } catch (Throwable t) { problems << ('rel ' + r.source + '->' + r.target + ': aggregation kind not set: ' + t.getClass().getSimpleName()) }
                relCreated++
            } else if (DEP_STEREO.containsKey(r.kind)) {
                def wantSt = DEP_STEREO[r.kind] == null ? null : sh.getStereotype(project, DEP_STEREO[r.kind], sysml)
                if (DEP_STEREO[r.kind] != null && wantSt == null) { problems << ('rel kind ' + r.kind + ': SysML stereotype ' + DEP_STEREO[r.kind] + ' not found'); return }
                def exists = src.get_directedRelationshipOfSource().any { d ->
                    try {
                        d.getTarget().contains(tgt) && (wantSt == null ? !sh.getStereotypes(d).any { s -> DEP_STEREO.values().contains(s.getName()) } : sh.hasStereotype(d, wantSt))
                    } catch (Throwable t) { false }
                }
                if (exists) { relSkipped++; return }
                def d = (wantSt != null) ? factory.createAbstractionInstance() : factory.createDependencyInstance()
                d.getClient().add(src)
                d.getSupplier().add(tgt)
                attach(d, src.getOwner())
                if (wantSt != null) sh.addStereotype(d, wantSt)
                relCreated++
            } else {
                problems << ('rel kind ' + r.kind + ': not implemented')
            }
        } catch (Throwable t) {
            problems << ('rel ' + r.source + '-' + r.kind + '->' + r.target + ': ' + t.toString())
        }
    }

    // ---- 4. leftovers (managed packages, unmanaged elements) --------------
    def planned = plan.elements.collect { it.id } as Set
    packages.values().unique().each { pkg ->
        pkg.getOwnedElement().each { el ->
            def st
            try { st = stereotypes.values().find { s -> sh.hasStereotype(el, s) } } catch (Throwable t) { return }
            if (st == null) return
            def raw
            try { raw = sh.getStereotypePropertyFirst(el, st, plan.spec_id_tag) }
            catch (Throwable t) { line('WARN: could not read spec id on "' + el.getName() + '": ' + t.getClass().getSimpleName()); return }
            if (raw == null) { line('UNMANAGED: stereotyped element without a spec id (created by hand?): "' + el.getName() + '"'); return }
            def sid = String.valueOf(raw)
            if (!planned.contains(sid)) line('LEFTOVER (in model, no longer in spec — not deleted; Phase 3): ' + sid + ' "' + el.getName() + '"')
        }
    }

    try { sessionMgr.closeSession(project) } catch (Throwable t) { sessionMgr.closeSession() }
} catch (Throwable t) {
    fatal = true
    problems << ('FATAL: ' + t.toString())
    try { sessionMgr.cancelSession(project) } catch (Throwable t2) { try { sessionMgr.cancelSession() } catch (Throwable ignored) { } }
}

// ------------------------------------------------------------ report
line('')
line('SUMMARY')
line('  elements created: ' + created + ', updated: ' + updated)
line('  relationships created: ' + relCreated + ', already existed: ' + relSkipped)
line('  problems: ' + problems.size() + (fatal ? '  (FATAL — session cancelled, project untouched)' : ''))
problems.each { line('  - ' + it) }
line('')
line(fatal ? 'OUTCOME: FAILED — nothing was committed.' :
     'OUTCOME: committed as ONE undoable session. Verify in the containment tree; one Ctrl+Z reverts the whole run. Save the project to keep it.')
line('END A5 GENERATION REPORT')

def out = new File(System.getProperty('user.home'), 'a5_generation_report.txt')
out.setText(report.toString(), 'UTF-8')
println(report.toString())
def statusLine = fatal ? ('A5: FAILED — ' + (problems ? problems[0] : 'see report'))
                       : ('A5: ' + created + ' created, ' + updated + ' updated, ' + relCreated +
                          ' relationships, ' + problems.size() + ' problems')
JOptionPane.showMessageDialog(null,
    'Generation ' + (fatal ? 'FAILED (project untouched)' : ('finished: ' + created + ' created, ' + updated + ' updated, ' +
    relCreated + ' relationships' + (problems.isEmpty() ? '' : ', ' + problems.size() + ' problem(s) — see report'))) +
    '\nReport: ' + out.getAbsolutePath() + '\n\nTell your assistant:  "' + statusLine + '"',
    'A5 Generator', (fatal || !problems.isEmpty()) ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE)

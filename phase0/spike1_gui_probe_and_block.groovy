// =============================================================================
// PHASE 0 — SPIKE 1: Open API probe + one-Block create/undo test (GUI macro)
// =============================================================================
// WHAT THIS PROVES (Aspect 2 feasibility, Phase 0 checkpoint):
//   S1. The Cameo/MagicDraw Open Java API is reachable from an in-JVM script.
//   S2. The SysML *v1* namespace (com.nomagic.magicdraw.sysml.*) is present.
//   S3. Introspection: the actual 2026x method surface of SessionManager,
//       ElementsFactory, ModelElementsManager, and undo-related project
//       methods (our API oracle — no reference docs are supplied, so we
//       print real signatures instead of guessing).
//   S4. One Block can be created inside a session, stereotyped, and reverted
//       two DIFFERENT ways — keep the distinction, they are not the same:
//         (a) SESSION-ABORT REVERT: cancelSession() aborts an *uncommitted*
//             session. This is rollback of in-flight work, NOT undo.
//         (b) POST-COMMIT UNDO: closeSession() commits, then the operator
//             presses Ctrl+Z. This is the true undo path the Aspect-1
//             requirement ("AI edits individually revertible") cares about.
//
// API VERIFICATION STATUS
//   Class/method names below are best public knowledge for the MagicDraw
//   Open API and are UNVERIFIED against 2026x specifically. That is why:
//     * every call is individually try/caught and reported, never fatal;
//     * ambiguous signatures are resolved by java.lang.reflect lookup at
//       runtime (not by exception-probing alone), and the report records
//       exactly which variant was used;
//     * calls that could not be confirmed are tagged [UNVERIFIED] in the
//       report so a reader can tell "environment broken" from "our guess
//       at the API name was wrong".
//
// HOW TO RUN (Cameo Systems Modeler 2026x, Windows 11):
//   1. Start Cameo. Create a NEW project: File > New Project > SysML Project,
//      name it  Phase0Probe  (any location). The SysML profile must be loaded,
//      so use the SysML project template, NOT a blank UML project.
//   2. Tools > Macros > Organize Macros... > Add (or Create):
//        - Name:            Phase0Spike1
//        - Macro Language:  Groovy
//        - File:            (browse to this file)
//      Save, then run it (Tools > Macros > Phase0Spike1).
//      (If your menu layout differs slightly, any way of running a Groovy
//       macro/script inside Cameo is fine — the script is self-contained.)
//   3. A dialog will pop up when it finishes, telling you where the report
//      file was written (your user home directory, phase0_report.txt).
//   4. Then do the ONE manual step printed at the end of the report:
//      press Ctrl+Z once and check whether the Block named
//      Phase0_UndoMe_KeepAfterClose disappears from the containment tree.
//   5. Close the Phase0Probe project WITHOUT saving (it is throwaway).
//
// HOW TO READ THE RESULT (no relay — you decide locally):
//   Open %USERPROFILE%\phase0_report.txt and look at the SUMMARY block.
//   - All load-bearing lines [PASS] and Ctrl+Z removed the Block
//       → environment is GOOD: proceed to the A2 profile builder.
//   - Any [FAIL] line → see the decision table in phase0/RUNBOOK.md for what
//     each failure means and what to do; keep the report file (and push it
//     with push_reports.bat if this machine has git) for troubleshooting.
// =============================================================================

import javax.swing.JOptionPane
import java.lang.reflect.Modifier

// ---------------------------------------------------------------- report state
StringBuilder report = new StringBuilder()
List<String> summary = []   // one [PASS]/[FAIL]/[WARN] line per check

def line = { String s -> report.append(s).append('\n') }
def header = { String s ->
    line(''); line('=' * 70); line('== ' + s); line('=' * 70)
}
def result = { String tag, String check, String detail ->
    summary << String.format('[%s] %-24s %s', tag, check, detail)
    line(String.format('>> [%s] %s — %s', tag, check, detail))
}

line('BEGIN PHASE0 REPORT (spike1_gui_probe_and_block.groovy)')
line('Generated: ' + new Date().toString())

// =============================================================== S1 — ENVIRONMENT
header('S1  ENVIRONMENT / OPEN API REACHABILITY')
def app = null
def project = null
try {
    app = com.nomagic.magicdraw.core.Application.getInstance()
    line('Application class loaded: ' + app.getClass().getName())

    // ApplicationEnvironment holds install root / version info, but exact
    // getter names vary by release — introspect static no-arg getters whose
    // names mention version/root/config and print whatever they return.
    def envCls = Class.forName('com.nomagic.magicdraw.core.ApplicationEnvironment')
    envCls.getMethods().findAll { m ->
        Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 &&
        (m.getName().toLowerCase().contains('version') ||
         m.getName().toLowerCase().contains('root') ||
         m.getName().toLowerCase().contains('config'))
    }.each { m ->
        try { line('  ApplicationEnvironment.' + m.getName() + '() = ' + String.valueOf(m.invoke(null))) }
        catch (Throwable t) { line('  ApplicationEnvironment.' + m.getName() + '() threw ' + t.toString()) }
    }
    line('  java.version   = ' + System.getProperty('java.version'))
    line('  groovy.version = ' + GroovySystem.getVersion())
    result('PASS', 'OPEN_API_REACHABLE', 'Application.getInstance() succeeded in-JVM')
} catch (Throwable t) {
    result('FAIL', 'OPEN_API_REACHABLE', t.toString())
}

try {
    project = app?.getProject()
    if (project == null) {
        result('FAIL', 'PROJECT_OPEN', 'No active project — open the Phase0Probe SysML project first, then re-run')
    } else {
        line('Active project: ' + project.getName())
        result('PASS', 'PROJECT_OPEN', 'Active project = ' + project.getName())
    }
} catch (Throwable t) {
    result('FAIL', 'PROJECT_OPEN', t.toString())
}

// ======================================================== S2 — V1 NAMESPACE CHECK
header('S2  SYSML V1 NAMESPACE (com.nomagic.magicdraw.sysml.*)')
// Aspect 2 is pinned to the v1 API namespace. Prove those classes exist in
// this install; the v2 namespace (com.dassault_systemes.modeler.sysml.*) is
// out of scope here and is NOT probed (its class names are unverified).
// Load-bearing classes report FAIL when missing; informational ones WARN.
[
    ['com.nomagic.magicdraw.sysml.util.SysMLProfile',            true ],
    ['com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper',       true ],
    ['com.nomagic.magicdraw.openapi.uml.SessionManager',         true ],
    ['com.nomagic.magicdraw.openapi.uml.ModelElementsManager',   false],  // preferred attach path, see S4
    ['com.nomagic.magicdraw.uml.ElementsFactory',                false]   // may live elsewhere; miss is informational
].each { probe ->
    String cn = probe[0]; boolean loadBearing = probe[1]
    try {
        Class.forName(cn)
        line('  FOUND    ' + cn)
        result('PASS', 'CLASS ' + cn.tokenize('.').last(), 'present')
    } catch (Throwable t) {
        line('  MISSING  ' + cn + '  (' + t.getClass().getSimpleName() + ')')
        result(loadBearing ? 'FAIL' : 'WARN', 'CLASS ' + cn.tokenize('.').last(), 'NOT found by Class.forName')
    }
}

// ========================================================== S3 — API INTROSPECTION
header('S3  INTROSPECTION — actual 2026x method surfaces (kept in this local report as the API reference)')
def dumpMethods = { String title, Class cls, Closure<Boolean> filter ->
    line(''); line('-- ' + title + ' (' + cls.getName() + ')')
    cls.getMethods().findAll { m -> m.getDeclaringClass() != Object.class && filter(m) }
       .collect { m -> '   ' + m.getReturnType().getSimpleName() + ' ' + m.getName() + '(' +
                        m.getParameterTypes().collect { it.getSimpleName() }.join(', ') + ')' }
       .toSorted().each { line(it) }
}
try {
    def smCls = Class.forName('com.nomagic.magicdraw.openapi.uml.SessionManager')
    dumpMethods('SessionManager — full public surface', smCls, { true })
    result('PASS', 'INTROSPECT_SESSIONMGR', 'method list captured')
} catch (Throwable t) { result('FAIL', 'INTROSPECT_SESSIONMGR', t.toString()) }

try {
    // ModelElementsManager is (per public docs) the canonical way to attach a
    // new element to a parent; dump its surface so the local report confirms it.
    def memCls = Class.forName('com.nomagic.magicdraw.openapi.uml.ModelElementsManager')
    dumpMethods('ModelElementsManager — full public surface', memCls, { true })
    result('PASS', 'INTROSPECT_ELEMMGR', 'method list captured')
} catch (Throwable t) { result('WARN', 'INTROSPECT_ELEMMGR', t.toString()) }

try {
    def ef = project?.getElementsFactory()
    if (ef != null) {
        dumpMethods('ElementsFactory — create*Class* methods only (full list is huge)', ef.getClass(),
                    { m -> m.getName().startsWith('create') && m.getName().contains('Class') })
        line('   (only create*Class* shown; total create methods: ' +
             ef.getClass().getMethods().count { it.getName().startsWith('create') } + ')')
        result('PASS', 'INTROSPECT_FACTORY', 'factory obtained from project')
    } else {
        result('FAIL', 'INTROSPECT_FACTORY', 'project.getElementsFactory() returned null / no project')
    }
} catch (Throwable t) { result('FAIL', 'INTROSPECT_FACTORY', t.toString()) }

try {
    // Undo surface: we need to know what programmatic undo looks like in 2026x.
    dumpMethods('Project — methods mentioning undo/redo/history/command', project.getClass(),
                { m -> m.getName().toLowerCase() =~ /undo|redo|history|command/ })
    result('PASS', 'INTROSPECT_UNDO', 'undo-related project methods captured')
} catch (Throwable t) { result('WARN', 'INTROSPECT_UNDO', t.toString()) }

// ============================================= S4 — BLOCK CREATE / REVERT / UNDO
header('S4  ONE BLOCK: session-abort revert (cancelSession) + post-commit undo (closeSession + Ctrl+Z)')

// --- signature discovery: confirm by java.lang.reflect BEFORE calling --------
// Exception-probing alone is fragile (a present-but-failing overload throws
// something other than MissingMethodException and would mask the fallback),
// so we look the method up reflectively first and only then dispatch.
def hasMethod = { Object target, String name, Class[] params ->
    try { target.getClass().getMethod(name, params); return true }
    catch (Throwable t) { return false }
}
Class projectIface = null
try { projectIface = Class.forName('com.nomagic.magicdraw.core.Project') } catch (Throwable ignored) { }

def sessionMgr = null
def createSession = { String name ->
    if (projectIface != null && hasMethod(sessionMgr, 'createSession', [projectIface, String] as Class[])) {
        sessionMgr.createSession(project, name); return 'createSession(Project,String) [reflection-confirmed]'
    }
    if (hasMethod(sessionMgr, 'createSession', [String] as Class[])) {
        sessionMgr.createSession(name); return 'createSession(String) [reflection-confirmed]'
    }
    sessionMgr.createSession(project, name); return 'createSession(Project,String) [UNVERIFIED — blind call]'
}
def closeSession = { ->
    if (projectIface != null && hasMethod(sessionMgr, 'closeSession', [projectIface] as Class[])) {
        sessionMgr.closeSession(project); return 'closeSession(Project) [reflection-confirmed]'
    }
    if (hasMethod(sessionMgr, 'closeSession', new Class[0])) {
        sessionMgr.closeSession(); return 'closeSession() [reflection-confirmed]'
    }
    sessionMgr.closeSession(project); return 'closeSession(Project) [UNVERIFIED — blind call]'
}
def cancelSession = { ->
    if (projectIface != null && hasMethod(sessionMgr, 'cancelSession', [projectIface] as Class[])) {
        sessionMgr.cancelSession(project); return 'cancelSession(Project) [reflection-confirmed]'
    }
    if (hasMethod(sessionMgr, 'cancelSession', new Class[0])) {
        sessionMgr.cancelSession(); return 'cancelSession() [reflection-confirmed]'
    }
    sessionMgr.cancelSession(project); return 'cancelSession(Project) [UNVERIFIED — blind call]'
}
def primaryModel = {
    if (hasMethod(project, 'getPrimaryModel', new Class[0])) return project.getPrimaryModel()
    return project.getModel()
}
// Attach a freshly created element to a parent. Preferred path is
// ModelElementsManager.addElement(element, parent) (public-docs canonical);
// fallback is element.setOwner(parent). Both are [UNVERIFIED] on 2026x until
// a run confirms — the report records which one actually ran.
def attach = { el, parent ->
    try {
        def mem = Class.forName('com.nomagic.magicdraw.openapi.uml.ModelElementsManager').getMethod('getInstance').invoke(null)
        mem.addElement(el, parent)
        return 'ModelElementsManager.addElement [UNVERIFIED name, succeeded at runtime]'
    } catch (Throwable t) {
        el.setOwner(parent)
        return 'setOwner fallback [UNVERIFIED name, succeeded at runtime; addElement failed: ' + t.getClass().getSimpleName() + ']'
    }
}
// Search the primary model's direct children; distinguishes "not found" from
// "the search itself errored" so a broken lookup can't fake a passing revert.
def findByName = { String n ->
    try {
        def hit = primaryModel().getOwnedElement().any { el ->
            try { el.getName() == n } catch (Throwable t) { false }
        }
        return [found: hit, error: null]
    } catch (Throwable t) {
        return [found: false, error: t.toString()]
    }
}

def blockStereo = null
try {
    sessionMgr = com.nomagic.magicdraw.openapi.uml.SessionManager.getInstance()
    def sh = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
    // [UNVERIFIED] profile/stereotype lookup names ("SysML", "Block") are the
    // publicly documented ones; a FAIL here may mean wrong lookup name, not a
    // broken environment — the introspection output above will disambiguate.
    def profile = sh.getProfile(project, 'SysML')
    if (profile == null) throw new IllegalStateException('SysML profile not found in project — was this created from the SysML project template?')
    blockStereo = sh.getStereotype(project, 'Block', profile)
    if (blockStereo == null) throw new IllegalStateException('Block stereotype not found in SysML profile')
    line('SysML profile + Block stereotype resolved OK')
    result('PASS', 'SYSML_PROFILE', 'Block stereotype resolved from project SysML profile')
} catch (Throwable t) {
    result('FAIL', 'SYSML_PROFILE', t.toString())
}

// --- S4a: create + CANCEL — session-abort revert of UNCOMMITTED work ----------
// NOTE: this is rollback of an open session, NOT undo of a committed edit.
try {
    def sig = createSession('Phase0 cancel test')
    line('Session opened via ' + sig)
    def cls = project.getElementsFactory().createClassInstance()   // [UNVERIFIED] factory method name
    cls.setName('Phase0_CancelMe_ShouldVanish')
    line('Attached via ' + attach(cls, primaryModel()))
    com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.addStereotype(cls, blockStereo)
    def appliedBefore = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.hasStereotype(cls, blockStereo)
    line('Block created + stereotyped inside session (stereotype applied = ' + appliedBefore + ')')
    def csig = cancelSession()
    line('Session cancelled via ' + csig)
    def lookup = findByName('Phase0_CancelMe_ShouldVanish')
    if (lookup.error != null) {
        result('WARN', 'CREATE_THEN_CANCEL', 'session-abort ran but post-cancel lookup errored: ' + lookup.error)
    } else if (!lookup.found && appliedBefore) {
        result('PASS', 'CREATE_THEN_CANCEL', 'session-abort revert OK: stereotyped Block created, cancelSession removed it')
    } else {
        result('FAIL', 'CREATE_THEN_CANCEL', 'stereotyped=' + appliedBefore + ', element still present after cancel=' + lookup.found)
    }
} catch (Throwable t) {
    result('FAIL', 'CREATE_THEN_CANCEL', t.toString())
    try { cancelSession() } catch (Throwable ignored) { }   // never leave a session dangling
}

// --- S4b: create + CLOSE — commits; operator's Ctrl+Z is the true undo test ---
try {
    def sig = createSession('Phase0 create one Block')
    def cls = project.getElementsFactory().createClassInstance()
    cls.setName('Phase0_UndoMe_KeepAfterClose')
    line('Attached via ' + attach(cls, primaryModel()))
    com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.addStereotype(cls, blockStereo)
    def csig = closeSession()
    def lookup = findByName('Phase0_UndoMe_KeepAfterClose')
    def applied = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.hasStereotype(cls, blockStereo)
    line('Session opened via ' + sig + ', committed via ' + csig)
    if (lookup.error != null) {
        result('WARN', 'CREATE_THEN_CLOSE', 'commit ran but post-commit lookup errored: ' + lookup.error + ' — check containment tree manually')
    } else if (lookup.found && applied) {
        result('PASS', 'CREATE_THEN_CLOSE', 'committed: Block «Block» Phase0_UndoMe_KeepAfterClose persisted; now test Ctrl+Z (post-commit undo)')
    } else {
        result('FAIL', 'CREATE_THEN_CLOSE', 'present=' + lookup.found + ', stereotyped=' + applied)
    }
} catch (Throwable t) {
    result('FAIL', 'CREATE_THEN_CLOSE', t.toString())
    try { cancelSession() } catch (Throwable ignored) { }
}

// ================================================================== SUMMARY
header('SUMMARY')
summary.each { line(it) }
line('')
line('Terminology: CREATE_THEN_CANCEL proves SESSION-ABORT revert (uncommitted work).')
line('The Ctrl+Z step below proves POST-COMMIT UNDO — the one Aspect 1 requires.')
line('')
line('MANUAL STEP FOR OPERATOR (do this now):')
line('  1. Look at the containment tree: you should see a Block named')
line('     Phase0_UndoMe_KeepAfterClose  (and NO element named Phase0_CancelMe_ShouldVanish).')
line('  2. Press Ctrl+Z (Edit > Undo) ONCE.')
line('  3. If the Block disappears, post-commit undo WORKS — note it as PASS for yourself.')
line('  4. Close the Phase0Probe project WITHOUT saving.')
line('  5. If everything above is PASS: proceed to derived/a2_profile_builder.groovy.')
line('     Any FAIL: see the decision table in phase0/RUNBOOK.md.')
line('')
line('END PHASE0 REPORT')

// ------------------------------------------------------------- write + notify
def out = new File(System.getProperty('user.home'), 'phase0_report.txt')
out.setText(report.toString(), 'UTF-8')
println(report.toString())
def failed = summary.count { it.startsWith('[FAIL]') }
JOptionPane.showMessageDialog(null,
    'Phase 0 spike finished: ' + (failed == 0 ? 'ALL CHECKS PASSED' : failed + ' FAILED check(s)') +
    '\nReport: ' + out.getAbsolutePath() +
    '\n\nNow do the Ctrl+Z step described at the end of the report.\n' +
    (failed == 0 ? 'If the Block disappears on Ctrl+Z, proceed to the A2 profile builder.'
                 : 'See the decision table in phase0/RUNBOOK.md before going further.'),
    'Phase 0 Spike 1', failed == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE)

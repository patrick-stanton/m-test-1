import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * PHASE 0 — headless feasibility spike.
 *
 * Purpose: decide the Phase-0 "headless or not" question. This program starts
 * the Cameo/MagicDraw core WITHOUT the GUI via the documented command-line
 * mechanism (subclass {@code com.nomagic.magicdraw.commandline.CommandLine},
 * launch through the OSGi framework launcher — see run_headless_check.bat).
 *
 * If {@link #execute()} runs at all, the core booted headless: it prints the
 * install/version info and confirms the SysML v1 namespace is on the
 * classpath. It deliberately does NOT open or modify any project — Phase 0
 * only needs a boot-and-probe, and keeping it read-only means it cannot
 * damage anything while we are still learning the 2026x API surface.
 *
 * Every probe is reflective and individually try/caught: the exact getter
 * names on ApplicationEnvironment vary across releases, and a partial report
 * is more useful than a stack trace (the local install is our API oracle).
 */
public class Phase0HeadlessCheck extends com.nomagic.magicdraw.commandline.CommandLine {

    @Override
    protected byte execute() {
        System.out.println("PHASE0-HEADLESS: execute() reached — core started WITHOUT GUI.");

        // --- version / install info, discovered reflectively -----------------
        try {
            Class<?> env = Class.forName("com.nomagic.magicdraw.core.ApplicationEnvironment");
            for (Method m : env.getMethods()) {
                String n = m.getName().toLowerCase();
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && (n.contains("version") || n.contains("root"))) {
                    try {
                        System.out.println("PHASE0-HEADLESS: ApplicationEnvironment." + m.getName()
                                + "() = " + m.invoke(null));
                    } catch (Throwable t) {
                        System.out.println("PHASE0-HEADLESS: " + m.getName() + "() threw " + t);
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("PHASE0-HEADLESS: ApplicationEnvironment probe failed: " + t);
        }

        // --- SysML v1 namespace present in headless classpath? ---------------
        String[] probes = {
                "com.nomagic.magicdraw.sysml.util.SysMLProfile",
                "com.nomagic.magicdraw.openapi.uml.SessionManager",
                "com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper"
        };
        for (String cn : probes) {
            try {
                Class.forName(cn);
                System.out.println("PHASE0-HEADLESS: FOUND   " + cn);
            } catch (Throwable t) {
                System.out.println("PHASE0-HEADLESS: MISSING " + cn + " (" + t.getClass().getSimpleName() + ")");
            }
        }

        System.out.println("PHASE0-HEADLESS: RESULT=SUCCESS");
        return 0;   // 0 = success exit code for the launcher
    }

    public static void main(String[] args) {
        new Phase0HeadlessCheck().launch(args);
    }
}

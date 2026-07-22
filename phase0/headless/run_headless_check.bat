@echo off
setlocal EnableDelayedExpansion
REM ===========================================================================
REM PHASE 0 - headless feasibility check for Cameo Systems Modeler 2026x
REM ===========================================================================
REM WHAT IT DOES
REM   1. Prints diagnostics about your install (jars, config files, bundled JDK)
REM   2. Lists + dumps the install's own openapi command-line examples
REM      (unconditionally - they are the canonical recipe if our launch fails)
REM   3. Compiles Phase0HeadlessCheck.java against the Cameo libraries
REM   4. Launches the Cameo core WITHOUT the GUI and runs the check
REM   All output is written to  phase0_headless_log.txt  next to this script.
REM
REM HOW TO RUN
REM   1. Edit MD_HOME below to your real install path. Common locations:
REM        C:\Program Files\Cameo Systems Modeler
REM        C:\Program Files\Catia No Magic\Cameo Systems Modeler 2026x
REM        C:\Users\<you>\AppData\Local\Cameo Systems Modeler 2026x
REM      (no trailing backslash, no quotes)
REM   2. Open "cmd", cd into this folder, run:  run_headless_check.bat
REM   3. The console prints the outcome at the end; the full log
REM      (phase0_headless_log.txt, next to this script) stays local for
REM      troubleshooting via the decision table in phase0\RUNBOOK.md.
REM
REM PREREQUISITE
REM   A Java COMPILER (javac, JDK 17+). Cameo may bundle only a runtime (JRE).
REM   The script looks for a bundled javac first, then one on PATH; if neither
REM   exists it says NO JAVA COMPILER FOUND and stops - that itself is a
REM   Phase 0 finding worth reporting in your own words.
REM
REM IMPORTANT / KNOWN UNKNOWNS (this is a spike, failure is a valid result):
REM   * The launch pattern below (OSGi ProductionFrameworkLauncher + patch.jar
REM     first on the classpath) is the publicly documented pattern for recent
REM     MagicDraw/Cameo releases. It is UNVERIFIED on 2026x specifically -
REM     if it fails, the error text in the log is exactly what we need, and
REM     the openapi example dump in section [2/5] gives us the exact working
REM     recipe for the next attempt without another round-trip.
REM   * main.class is passed BOTH as a JVM -D property and as a launcher
REM     argument, because which one ProductionFrameworkLauncher honors is
REM     unverified on 2026x - supplying both is harmless and covers either.
REM   * Headless startup still consumes a license. If it stalls or errors on
REM     licensing, that too is a Phase 0 finding - report it in your own words.
REM ===========================================================================

REM ======== EDIT THIS LINE (no trailing backslash, no quotes) ================
set MD_HOME=C:\Program Files\Cameo Systems Modeler
REM ===========================================================================

set LOG=%~dp0phase0_headless_log.txt
echo BEGIN PHASE0 HEADLESS LOG > "%LOG%"
echo MD_HOME=%MD_HOME% >> "%LOG%"

echo ---- [1/5] install diagnostics ---- >> "%LOG%"
for %%F in ("lib\patch.jar" "lib\md.jar" "lib\md_api.jar" "lib\md_common.jar" "lib\md_common_api.jar" "lib\brand.jar" "lib\brand_api.jar" "data\application.conf" "data\logback.xml" "configuration" "openapi") do (
    if exist "%MD_HOME%\%%~F" (echo   OK      %%~F >> "%LOG%") else (echo   MISSING %%~F >> "%LOG%")
)

REM ---- locate java + javac: prefer the JDK bundled with Cameo ---------------
set JAVA_EXE=
set JAVAC_EXE=
for %%D in (jre jdk java) do (
    if exist "%MD_HOME%\%%D\bin\java.exe"  set JAVA_EXE=%MD_HOME%\%%D\bin\java.exe
    if exist "%MD_HOME%\%%D\bin\javac.exe" set JAVAC_EXE=%MD_HOME%\%%D\bin\javac.exe
)
if not defined JAVA_EXE  set JAVA_EXE=java
if not defined JAVAC_EXE set JAVAC_EXE=javac
echo   JAVA_EXE=!JAVA_EXE!  >> "%LOG%"
echo   JAVAC_EXE=!JAVAC_EXE! >> "%LOG%"
"!JAVA_EXE!" -version >> "%LOG%" 2>&1

echo ---- [2/5] openapi examples (canonical launch recipes shipped in-install) ---- >> "%LOG%"
if exist "%MD_HOME%\openapi" (
    echo   -- dir /b openapi: >> "%LOG%"
    dir /b "%MD_HOME%\openapi" >> "%LOG%" 2>&1
    echo   -- launch scripts under openapi ^(recursive^): >> "%LOG%"
    dir /s /b "%MD_HOME%\openapi\*.bat" "%MD_HOME%\openapi\*.properties" >> "%LOG%" 2>nul
    set DUMPED=0
    for /f "delims=" %%B in ('dir /s /b "%MD_HOME%\openapi\*.bat" 2^>nul') do (
        if !DUMPED! lss 3 (
            echo   ----- CONTENT OF %%B ----- >> "%LOG%"
            type "%%B" >> "%LOG%" 2>&1
            set /a DUMPED+=1
        )
    )
) else (
    echo   openapi folder NOT found under MD_HOME >> "%LOG%"
)

set MD_CP=%MD_HOME%\lib\patch.jar;%MD_HOME%\lib\brand_api.jar;%MD_HOME%\lib\brand.jar;%MD_HOME%\lib\md_common_api.jar;%MD_HOME%\lib\md_common.jar;%MD_HOME%\lib\md_api.jar;%MD_HOME%\lib\md.jar;%MD_HOME%\lib\*

echo ---- [3/5] compile ---- >> "%LOG%"
"!JAVAC_EXE!" -version >> "%LOG%" 2>&1
if errorlevel 1 (
    echo   NO JAVA COMPILER FOUND - install any JDK 17+ or point JAVAC_EXE at one. >> "%LOG%"
    echo   FINDING: cannot compile on this machine; headless stays undecided. >> "%LOG%"
    goto :done
)
"!JAVAC_EXE!" -cp "%MD_CP%" -d "%~dp0" "%~dp0Phase0HeadlessCheck.java" >> "%LOG%" 2>&1
if errorlevel 1 (
    echo   COMPILE FAILED - see errors above. >> "%LOG%"
    goto :done
)
echo   compile OK >> "%LOG%"

echo ---- [4/5] headless launch ---- >> "%LOG%"
REM main.class is supplied both as a real -D (before the main class) and as a
REM launcher argument (after it) - see KNOWN UNKNOWNS above.
"!JAVA_EXE!" -Xmx2000M ^
  -Dmain.class=Phase0HeadlessCheck ^
  -Dmd.class.path=$java.class.path ^
  -Dcom.nomagic.osgi.config.dir="%MD_HOME%\configuration" ^
  -Desi.system.config="%MD_HOME%\data\application.conf" ^
  -Dlogback.configurationFile="%MD_HOME%\data\logback.xml" ^
  -Dmd.plugins.dir="%MD_HOME%\plugins" ^
  -cp "%~dp0;%MD_CP%" ^
  com.nomagic.osgi.launcher.ProductionFrameworkLauncher -Dmain.class=Phase0HeadlessCheck ^
  >> "%LOG%" 2>&1
echo ---- [5/5] exit code: %ERRORLEVEL% ---- >> "%LOG%"

:done
echo END PHASE0 HEADLESS LOG >> "%LOG%"
echo.
echo Finished. Full log: %LOG%
findstr /c:"RESULT=SUCCESS" "%LOG%" >nul 2>&1
if not errorlevel 1 (
    echo OUTCOME: HEADLESS WORKS - tell your assistant "headless: success".
) else (
    echo OUTCOME: headless did NOT succeed. Open the log and use the decision
    echo table in phase0\RUNBOOK.md ^(Step 2^) - the log's section [2/5] contains
    echo your install's own example launch scripts to mirror. Tell your
    echo assistant in your own words what the last error line says.
)
endlocal

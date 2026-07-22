@echo off
REM ===========================================================================
REM push_reports.bat — OPTIONAL: archive the pipeline report files into git.
REM
REM Nothing in the pipeline requires this. It exists because the operator
REM types feedback in their own words instead of pasting logs — this script
REM preserves the full evidence anyway, locally in reports\ and (if this
REM machine has git configured for the repo) as a pushed commit, so detailed
REM troubleshooting is possible later without anyone copying text by hand.
REM
REM Run from the fable-model-as-code folder:  push_reports.bat
REM ===========================================================================
setlocal
set DEST=%~dp0reports
if not exist "%DEST%" mkdir "%DEST%"

set COPIED=0
for %%F in (phase0_report.txt a2_profile_report.txt a5_generation_report.txt) do (
    if exist "%USERPROFILE%\%%F" (
        copy /y "%USERPROFILE%\%%F" "%DEST%\%%F" >nul
        echo   archived %%F
        set COPIED=1
    )
)
if exist "%~dp0phase0\headless\phase0_headless_log.txt" (
    copy /y "%~dp0phase0\headless\phase0_headless_log.txt" "%DEST%\phase0_headless_log.txt" >nul
    echo   archived phase0_headless_log.txt
    set COPIED=1
)
if "%COPIED%"=="0" (
    echo No report files found yet - run a pipeline step first.
    goto :end
)

where git >nul 2>&1
if errorlevel 1 (
    echo git is not installed on this machine - reports are archived locally in:
    echo   %DEST%
    goto :end
)
git -C "%~dp0" rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
    echo This folder is not a git checkout - reports are archived locally in:
    echo   %DEST%
    goto :end
)
git -C "%~dp0" add reports
git -C "%~dp0" commit -m "Archive operator report files" >nul
git -C "%~dp0" push
if errorlevel 1 (
    echo Push failed ^(no remote access?^) - reports are still committed locally.
) else (
    echo Reports pushed to the repository.
)
:end
endlocal

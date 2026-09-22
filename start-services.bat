@echo off
rem ===========================================================================
rem  Frozen Seafood Traceability System (FSTS) - local service launcher
rem ---------------------------------------------------------------------------
rem  Thin entry point for start-services.ps1, made for double-clicking.
rem  All startup / stop / status logic AND all user-facing messages (Chinese)
rem  live in start-services.ps1, which is stored as UTF-8 with BOM so every
rem  PowerShell host reads it correctly.
rem
rem  This .bat is intentionally pure ASCII: cmd.exe reads batch files using the
rem  console code page, so Chinese characters baked into a .bat turn into
rem  mojibake on machines whose code page is not GBK (and can even break
rem  parsing). Keeping the launcher ASCII-only makes it work on any locale.
rem
rem  Usage:
rem    start-services.bat                                    double-click:
rem                                                          db + backend +
rem                                                          frontend, then
rem                                                          open browser and
rem                                                          wait for a key
rem    start-services.bat status                             show status
rem    start-services.bat down                               stop backend+frontend
rem    start-services.bat down -StopDatabase                  also stop MySQL
rem    start-services.bat restart                            restart
rem    start-services.bat up -InitDatabase -RebuildBackend    rebuild db + jar
rem
rem  Every argument is forwarded to start-services.ps1 unchanged. Arguments are
rem  only defaulted when none are given, and the "pause at the end" behaviour is
rem  requested in that case only, so terminal usage stays script friendly.
rem ===========================================================================

setlocal EnableExtensions
cd /d "%~dp0"

set "PS1=%~dp0start-services.ps1"
if not exist "%PS1%" (
    echo [ERROR] start-services.ps1 not found next to this file.
    pause
    exit /b 1
)

set "PSEXE="
where pwsh.exe >nul 2>nul && set "PSEXE=pwsh.exe"
if not defined PSEXE (
    where powershell.exe >nul 2>nul && set "PSEXE=powershell.exe"
)
if not defined PSEXE (
    echo [ERROR] PowerShell not found. Install PowerShell 7 ^(pwsh^) or enable powershell.exe.
    pause
    exit /b 1
)

set "ARGS=%*"
if "%ARGS%"=="" set "ARGS=up -OpenBrowser -PauseAtEnd"

"%PSEXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %ARGS%
exit /b %ERRORLEVEL%

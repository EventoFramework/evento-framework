@echo off
setlocal enabledelayedexpansion

REM Licenses aggregator entry point for Windows
REM - If Python is available, run script\licenses.py (includes GUI via npx when possible)
REM - Otherwise, fall back to Gradle-only tasks to generate backend reports and notices

pushd "%~dp0" >NUL 2>&1
set ROOT=%CD%

echo [INFO] Running license report from: %ROOT%

where python >NUL 2>&1 && goto HASPY
goto FALLBACK

:HASPY
echo [INFO] Python detected. Executing script\licenses.py %*
python "%ROOT%\script\licenses.py" %*
set EXITCODE=%ERRORLEVEL%
if %EXITCODE% neq 0 (
  echo [ERROR] Python license script failed with code %EXITCODE%.
  popd >NUL 2>&1
  exit /b %EXITCODE%
)
echo [INFO] License workflow completed via Python script.
popd >NUL 2>&1
exit /b 0

:FALLBACK
echo [WARN] Python not found. Falling back to Gradle-only tasks.

if not exist "%ROOT%\gradlew.bat" (
  echo [ERROR] gradlew.bat not found at %ROOT%. Aborting.
  popd >NUL 2>&1
  exit /b 1
)

call "%ROOT%\gradlew.bat" --no-daemon --console=plain generateAllLicenseReports aggregateLicenses generateThirdPartyNotices
set EXITCODE=%ERRORLEVEL%
if %EXITCODE% neq 0 (
  echo [ERROR] Gradle license tasks failed with code %EXITCODE%.
  popd >NUL 2>&1
  exit /b %EXITCODE%
)

echo [INFO] Backend license reports generated under build\licenses
if exist "%ROOT%\build\licenses\combined.json" echo [INFO] Combined JSON: build\licenses\combined.json
if exist "%ROOT%\THIRD-PARTY-NOTICES.md" echo [INFO] Third-party notices: THIRD-PARTY-NOTICES.md

popd >NUL 2>&1
exit /b 0

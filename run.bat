@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if not exist "mvnw.cmd" (
  echo [ERROR] mvnw.cmd not found. Run this from the scheduler folder.
  exit /b 1
)

if not exist ".env" (
  echo [WARN] Missing .env — using application.properties defaults / process env.
  echo        Copy .env.example to .env for local overrides.
) else (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" (
      set "%%a=%%b"
    )
  )
)

rem Default 8010 so it does not collide with Mosonic scheduler (:8003).
if not defined SCHEDULER_PORT set SCHEDULER_PORT=8010
set "SERVER_PORT=%SCHEDULER_PORT%"

echo Checking port %SCHEDULER_PORT%...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%SCHEDULER_PORT%') do (
  echo Killing PID %%a ...
  taskkill /PID %%a /F >nul 2>&1
)

echo [INFO] Starting laconic scheduler on port %SCHEDULER_PORT%...
echo [INFO] Health: http://localhost:%SCHEDULER_PORT%/actuator/health
call mvnw.cmd spring-boot:run %*
exit /b %errorlevel%

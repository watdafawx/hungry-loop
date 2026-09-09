@echo off
REM Runs gradlew with a guaranteed JDK 21. Order of preference:
REM   1. a JDK unpacked into toolchain\jdk-*  (portable, nothing installed system-wide)
REM   2. Eclipse Adoptium JDK 21 in Program Files
REM If you have your own JDK 21 on PATH / JAVA_HOME you can ignore this and just run "gradlew".
REM
REM Why this exists: NeoForm has to recompile Minecraft, which needs javax.tools - a JRE does not
REM have it and the build dies with a confusing "JavaCompiler is null" NPE. Must be a full JDK.
setlocal
for /d %%D in ("%~dp0toolchain\jdk-*") do set "JAVA_HOME=%%D"
if not defined JAVA_HOME (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%D"
)
if not defined JAVA_HOME (
    echo No JDK 21 found. Put one in toolchain\ or set JAVA_HOME, then run gradlew build.
    exit /b 1
)
echo Using JDK: %JAVA_HOME%
call "%~dp0gradlew.bat" %*

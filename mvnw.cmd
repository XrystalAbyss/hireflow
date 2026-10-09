@echo off
setlocal
if not defined JAVA_HOME (
    if exist "%USERPROFILE%\.jdks\temurin-21" (
        set "JAVA_HOME=%USERPROFILE%\.jdks\temurin-21"
    )
)
if not defined MAVEN_HOME (
    if exist "%USERPROFILE%\.tools\apache-maven-3.9.9" (
        set "MAVEN_HOME=%USERPROFILE%\.tools\apache-maven-3.9.9"
    )
)
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
"%MAVEN_HOME%\bin\mvn.cmd" %*

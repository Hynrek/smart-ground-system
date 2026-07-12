@REM Maven Wrapper startup script for Windows
@echo off
set MAVEN_PROJECTBASEDIR=%DIRNAME%
if not "%MAVEN_PROJECTBASEDIR%"=="" goto endBaseDir
set MAVEN_PROJECTBASEDIR=%DirName%
:endBaseDir
set %MAVEN_PROJECTBASEDIR%\mvnw.cmd
set ERROR_CODE=0
@mvnw.cmd %*
if ERROR_LEVEL 9009 (set ERROR_CODE=1 & goto end)
:end
@exit /B %ERROR_CODE%
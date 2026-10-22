@echo off
rem HTMLPlayForge launcher: uses the system "gradle" command (Gradle 8.5).
set DIR=%~dp0
if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  gradle %*
)

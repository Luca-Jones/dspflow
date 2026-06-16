@echo off
cd /d %~dp0
if not exist dspflow.jar call build.bat
java -jar dspflow.jar %*

@echo off
rem Build DSPFlow on Windows (requires JDK 11+)
cd /d %~dp0
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
del sources.txt
if not exist out\dspflow\resources mkdir out\dspflow\resources
copy /y src\dspflow\resources\*.py out\dspflow\resources\ >nul
jar --create --file dspflow.jar --main-class dspflow.Main -C out .
echo Build OK. Run with run.bat  (or: java -jar dspflow.jar)

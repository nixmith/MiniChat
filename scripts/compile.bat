@echo off
setlocal enabledelayedexpansion

echo ===================================
echo MiniChat Build Script
echo ===================================

rem Clean and create directories
echo Cleaning previous build...
if exist out rmdir /s /q out
if exist jar rmdir /s /q jar
mkdir out
mkdir jar

rem Find all Java source files
echo Collecting source files...
dir /s /b server\src\main\java\*.java > sources.txt
dir /s /b client\src\main\java\*.java >> sources.txt

rem Count source files (Windows method)
for /f %%a in ('type sources.txt ^| find /c /v ""') do set FILE_COUNT=%%a
echo Found %FILE_COUNT% source files

rem Compile all sources
echo Compiling Java sources...
javac -encoding UTF-8 -d out @sources.txt
if %errorlevel% neq 0 (
    echo Compilation failed!
    del sources.txt
    exit /b 1
)

echo Compilation successful!

rem Create server JAR
echo Creating server.jar...
jar --create --file jar\server.jar --main-class=minichat.server.Server -C out .

rem Create client JAR
echo Creating client.jar...
jar --create --file jar\client.jar --main-class=minichat.client.Client -C out .

rem Create client GUI JAR
echo Creating client-gui.jar...
jar --create --file jar\client-gui.jar --main-class=minichat.client.gui.GuiClient -C out .

rem Clean up
del sources.txt

echo.
echo ===================================
echo Build Complete!
echo ===================================
echo Generated files:
echo   - jar\server.jar
echo   - jar\client.jar
echo   - jar\client-gui.jar
echo.
echo To run:
echo   Server: java -jar jar\server.jar ^<port^>
echo   Client: java -jar jar\client.jar ^<host^> ^<port^>
echo   GUI:    java -jar jar\client-gui.jar
echo ===================================
@echo off
setlocal enabledelayedexpansion

rem Clean and create directories
if exist out rmdir /s /q out
if exist jar rmdir /s /q jar
mkdir out
mkdir jar

rem Find all Java source files
echo Collecting Java sources...
dir /s /b server\src\main\java\*.java > sources.txt
dir /s /b client\src\main\java\*.java >> sources.txt

rem Compile all sources
echo Compiling Java sources...
javac -encoding UTF-8 -d out @sources.txt
if %errorlevel% neq 0 (
    echo Compilation failed!
    del sources.txt
    exit /b 1
)

rem Create server JAR
echo Creating server.jar...
jar --create --file jar\server.jar --main-class=minichat.server.Server -C out .

rem Create client JAR
echo Creating client.jar...
jar --create --file jar\client.jar --main-class=minichat.client.Client -C out .

rem Clean up
del sources.txt

echo Build complete!
echo Built jar\server.jar and jar\client.jar
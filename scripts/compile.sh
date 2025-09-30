#!/usr/bin/env bash
set -euo pipefail

echo "==================================="
echo "MiniChat Build Script"
echo "==================================="

# Clean and create directories
echo "Cleaning previous build..."
rm -rf out jar
mkdir -p out jar

# Find all Java source files
echo "Collecting source files..."
find server/src/main/java -name "*.java" > sources.txt
find client/src/main/java -name "*.java" >> sources.txt

# Count source files
FILE_COUNT=$(wc -l < sources.txt)
echo "Found $FILE_COUNT source files"

# Compile all sources
echo "Compiling Java sources..."
javac -encoding UTF-8 -d out @sources.txt

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
else
    echo "Compilation failed!"
    rm -f sources.txt
    exit 1
fi

# Create server JAR
echo "Creating server.jar..."
jar --create --file jar/server.jar --main-class=minichat.server.Server -C out .

# Create client JAR
echo "Creating client.jar..."
jar --create --file jar/client.jar --main-class=minichat.client.Client -C out .

# Create client GUI JAR
echo "Creating client-gui.jar..."
jar --create --file jar/client-gui.jar --main-class=minichat.client.gui.GuiClient -C out .

# Clean up
rm -f sources.txt

echo ""
echo "==================================="
echo "Build Complete!"
echo "==================================="
echo "Generated files:"
echo "  - jar/server.jar"
echo "  - jar/client.jar"
echo "  - jar/client-gui.jar"
echo ""
echo "To run:"
echo "  Server: java -jar jar/server.jar <port>"
echo "  Client: java -jar jar/client.jar <host> <port>"
echo "  GUI:    java -jar jar/client-gui.jar"
echo "==================================="
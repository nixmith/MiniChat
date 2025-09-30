#!/usr/bin/env bash
set -euo pipefail

# Clean and create directories
rm -rf out jar
mkdir -p out jar

# Find all Java source files
find server/src/main/java -name "*.java" > sources.txt
find client/src/main/java -name "*.java" >> sources.txt

# Compile all sources
echo "Compiling Java sources..."
javac -encoding UTF-8 -d out @sources.txt

# Create server JAR
echo "Creating server.jar..."
jar --create --file jar/server.jar --main-class=minichat.server.Server -C out .

# Create client JAR
echo "Creating client.jar..."
jar --create --file jar/client.jar --main-class=minichat.client.Client -C out .

# Create client GUI JAR
echo "Creating client-gui.jar..."
jar --create --file jar/client-gui.jar --main-class=minichat.client.GuiClient -C out .

# Clean up
rm -f sources.txt

echo "Build complete!"

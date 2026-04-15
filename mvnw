#!/bin/sh
# Maven Wrapper script

set -e

# Find the project base directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"

# Download wrapper jar if not present
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Maven Wrapper..."
    WRAPPER_URL=$(grep "wrapperUrl" "$WRAPPER_PROPERTIES" | cut -d'=' -f2)
    mkdir -p "$SCRIPT_DIR/.mvn/wrapper"
    if command -v curl > /dev/null 2>&1; then
        curl -sL "$WRAPPER_URL" -o "$WRAPPER_JAR"
    elif command -v wget > /dev/null 2>&1; then
        wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
    else
        echo "Error: curl or wget required to download Maven wrapper"
        exit 1
    fi
fi

# Get Maven distribution URL
DIST_URL=$(grep "distributionUrl" "$WRAPPER_PROPERTIES" | cut -d'=' -f2)

# Use Maven wrapper jar or fallback to direct download
if [ -f "$WRAPPER_JAR" ]; then
    exec java "-Dmaven.multiModuleProjectDirectory=$SCRIPT_DIR" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
else
    # Fallback: Download and extract Maven directly
    MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"
    if [ ! -d "$MAVEN_HOME" ]; then
        echo "Downloading Apache Maven 3.9.6..."
        mkdir -p "$HOME/.m2/wrapper/dists"
        TEMP_ZIP="/tmp/apache-maven-3.9.6-bin.zip"
        curl -sL "$DIST_URL" -o "$TEMP_ZIP"
        unzip -q "$TEMP_ZIP" -d "$HOME/.m2/wrapper/dists"
        rm "$TEMP_ZIP"
    fi
    exec "$MAVEN_HOME/bin/mvn" "$@"
fi

#!/bin/sh
if [ -z "$JAVA_HOME" ] && [ -d "$HOME/.jdks/temurin-21" ]; then
    export JAVA_HOME="$HOME/.jdks/temurin-21"
fi
if [ -z "$MAVEN_HOME" ] && [ -d "$HOME/.tools/apache-maven-3.9.9" ]; then
    export MAVEN_HOME="$HOME/.tools/apache-maven-3.9.9"
fi
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
exec mvn "$@"

#!/bin/sh
#
# Gradle wrapper launcher script (POSIX shell) — mushaf app build entry point.
#

# Resolve links: $0 may be a link
PRG="$0"
while [ -h "$PRG" ]; do
  ls_out=$(ls -ld "$PRG")
  link=$(expr "$ls_out" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done
SAVED="$(pwd)"
APP_HOME=$(cd "$(dirname "$PRG")" > /dev/null && pwd)
cd "$SAVED" > /dev/null || exit 1

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
else
  JAVACMD=java
fi

if ! command -v "$JAVACMD" > /dev/null 2>&1; then
  if [ -n "$JAVA_HOME" ]; then
    echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
  else
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
  fi
  echo "Please set the JAVA_HOME variable in your environment to match the location of your Java installation." >&2
  exit 1
fi

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
APP_BASE_NAME=${0##*/}

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$CLASSPATH" ]; then
  echo "ERROR: gradle-wrapper.jar not found at $CLASSPATH" >&2
  exit 1
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"

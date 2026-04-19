!/bin/sh
# Gradle wrapper script
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
WRAPPER_JAR="$GRADLE_USER_HOME/wrapper/dists/gradle-8.2-bin/gradle-8.2/lib/gradle-launcher-8.2.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    exec gradle "$@"
else
    exec java -jar "$WRAPPER_JAR" "$@"
fi

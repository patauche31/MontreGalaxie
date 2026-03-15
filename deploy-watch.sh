#!/bin/bash
# Déploie l'APK montre UNIQUEMENT sur la Galaxy Watch
# Usage : ./deploy-watch.sh PORT
# Exemple : ./deploy-watch.sh 33629

PORT=${1:?Erreur : donne le port ADB. Ex: ./deploy-watch.sh 33629}
ADB="/c/Users/patau/AppData/Local/Android/Sdk/platform-tools/adb.exe"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "▶ Build montre..."
JAVA_HOME="/c/Program Files/Java/jdk-18.0.1" PATH="$JAVA_HOME/bin:$PATH" \
    ./gradlew :app:assembleDebug 2>&1 | grep -E "(BUILD|error:)"

echo "▶ Connexion montre 192.168.137.148:$PORT ..."
$ADB connect "192.168.137.148:$PORT"

echo "▶ Installation sur la montre uniquement..."
$ADB -s "192.168.137.148:$PORT" install -r "$APK"
echo "✅ Montre mise à jour."

# PiscineTimer — Installation sur nouveau PC (MacBook Air)

## Ce qu'il faut installer en premier

- [ ] **Android Studio** : https://developer.android.com/studio
- [ ] **JDK 18** : https://www.oracle.com/java/technologies/javase/jdk18-archive-downloads.html
- [ ] **Git** : https://git-scm.com (ou via brew : `brew install git`)
- [ ] **ADB** : inclus dans Android Studio (platform-tools)

---

## Etape 1 — Copier les projets depuis la cle USB

Copie ces 2 dossiers vers ton Mac :
```
cle USB\PiscineTimer_Backup\MontreGalaxie\        →  ~/Documents/MontreGalaxie/
cle USB\PiscineTimer_Backup\PiscineTimerPhone\     →  ~/Documents/PiscineTimerPhone/
```

---

## Etape 2 — Ouvrir dans Android Studio

### Projet montre (Wear OS) :
1. Android Studio → `File` → `Open`
2. Selectionner `~/Documents/MontreGalaxie/`
3. Attendre le Gradle sync (5-10 min, telechargement des dependances)

### Projet telephone :
1. Android Studio → `File` → `Open`
2. Selectionner `~/Documents/PiscineTimerPhone/`
3. Attendre le Gradle sync

---

## Etape 3 — Verifier local.properties

Dans chaque projet, ouvrir `local.properties` et verifier :
```properties
sdk.dir=/Users/TON_NOM/Library/Android/sdk
```
Android Studio le cree automatiquement — verifier que le chemin est correct.

---

## Etape 4 — Configurer ADB WiFi pour la montre

La montre se connecte en WiFi (pas USB).

### Reseau necessaire :
- PC et montre sur le meme reseau WiFi
- IP montre : 192.168.137.148 (si hotspot PC Windows)
- ⚠️ Si reseau different, l'IP peut changer → verifier dans Parametres montre

### Trouver le port :
Sur la montre : Parametres → Options developpeurs → Debogage via Wi-Fi → noter IP:PORT

### Connecter :
```bash
# Dans Terminal Mac
~/Library/Android/sdk/platform-tools/adb connect 192.168.137.148:PORT
```

### Ajouter adb au PATH (pour ne pas taper le chemin complet) :
Ajouter dans `~/.zshrc` :
```bash
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
```
Puis : `source ~/.zshrc`

---

## Etape 5 — Deployer sur la montre

```bash
cd ~/Documents/MontreGalaxie

# Build
./gradlew :app:assembleDebug

# Installer sur la montre (remplacer PORT)
adb -s 192.168.137.148:PORT install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Etape 6 — Deployer sur le telephone (Redmi 12 5G)

```bash
cd ~/Documents/PiscineTimerPhone

# Brancher le telephone en USB ou connecter en WiFi
adb devices  # noter le serial du telephone

# Build et installer
./gradlew :app:assembleDebug
adb -s SERIAL_TELEPHONE install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Etape 7 — Recuperer les CSV de debug (apres seance piscine)

```bash
# Lister les fichiers CSV sur la montre
adb -s 192.168.137.148:PORT shell run-as com.piscine.timer ls files/

# Telecharger un fichier
adb -s 192.168.137.148:PORT shell run-as com.piscine.timer cat files/sensor_debug_XXXXXX.csv > ~/Desktop/session.csv
```

---

## Infos importantes du projet

| Element              | Valeur                        |
|----------------------|-------------------------------|
| Package              | com.piscine.timer             |
| minSdk               | 30 (Wear OS 3)                |
| targetSdk            | 34 ⚠️ (pas 35 = crash)        |
| Kotlin               | 2.0.0                         |
| AGP                  | 8.5.2                         |
| Gradle               | 8.9 ⚠️ (pas 9.x = incompatible KAPT) |
| JDK                  | 18 ⚠️ (pas 11 = incompatible) |
| IP montre            | 192.168.137.148               |
| Serial telephone     | e82daed81e77                  |

---

## En cas de probleme

**Gradle sync echoue :**
- Verifier que JDK 18 est selectionne : Android Studio → Settings → Build → Gradle → JDK

**adb : command not found :**
- Verifier le PATH dans ~/.zshrc

**INSTALL_FAILED_USER_RESTRICTED :**
- Confirmer l'installation sur l'ecran du telephone/montre

**Port montre introuvable :**
- Le port change a chaque reboot → aller dans Parametres montre → Options developpeurs → Debogage Wi-Fi

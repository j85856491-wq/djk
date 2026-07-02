# DJK Clean — version Android native (Kotlin)

Ce projet remplace l'ancienne version Kivy/Python par une application **Android native
en Kotlin**, compilée avec Gradle. C'est la technologie officielle recommandée par
Google pour Android — beaucoup plus stable et fiable que Kivy, et elle fonctionne sur
**toutes les versions d'Android depuis Android 5.0 (API 21) jusqu'à la plus récente**.

## Fonctionnement (identique au script Python d'origine)
- Bouton **Nettoyer** : déplace tous les médias de DCIM, Pictures, Movies et
  WhatsApp/Media vers un dossier caché `.djk_corbeille`, invisible dans la galerie.
- Barre de recherche : taper `etienne` fait apparaître le bouton **Restaurer**, qui
  remet tout à sa place d'origine.
- Rien n'est jamais supprimé définitivement, seulement déplacé.

## Étapes pour compiler l'APK via GitHub (sans Android Studio)

### 1. Créer le dépôt GitHub
1. Va sur [github.com/new](https://github.com/new) et crée un nouveau dépôt (peut être
   privé, puisque c'est pour ton usage personnel).
2. Ne coche aucune case d'initialisation (pas de README, pas de .gitignore — ils sont
   déjà inclus ici).

### 2. Envoyer les fichiers
Sur ton PC, dans le dossier `DJKClean` que je t'ai fourni :
```bash
cd DJKClean
git init
git add .
git commit -m "Version Kotlin native"
git branch -M main
git remote add origin https://github.com/TON-NOM-UTILISATEUR/TON-DEPOT.git
git push -u origin main
```
(Tu peux aussi simplement glisser-déposer tous les fichiers/dossiers sur la page web
de GitHub via "Add file > Upload files", si tu ne veux pas utiliser `git` en ligne de
commande — assure-toi de garder la même arborescence de dossiers.)

### 3. Lancer la compilation
Dès que le code est poussé sur la branche `main`, l'onglet **Actions** de ton dépôt
GitHub va automatiquement démarrer la compilation (grâce au fichier
`.github/workflows/build.yml`). Tu peux aussi la relancer manuellement depuis
Actions > Build APK > "Run workflow".

Compte environ 3 à 6 minutes.

### 4. Télécharger l'APK
Une fois le workflow terminé (coche verte ✅) :
1. Clique sur l'exécution du workflow dans l'onglet **Actions**.
2. Descends jusqu'à la section **Artifacts**.
3. Télécharge `DJKClean-apk` (fichier .zip contenant `app-debug.apk`).

### 5. Installer sur ton téléphone
1. Transfère `app-debug.apk` sur ton téléphone (câble USB, Google Drive, etc.).
2. Ouvre le fichier — Android va demander d'autoriser l'installation depuis cette
   source (à activer une fois dans les réglages).
3. Installe.
4. Au premier lancement, appuie sur **Nettoyer** : l'app va te rediriger vers les
   réglages système pour autoriser l'« accès à tous les fichiers » (obligatoire sur
   Android 11 et plus pour déplacer des fichiers hors des dossiers standards). Active
   l'option, reviens dans l'app, et relance.

## Pourquoi cette version est plus fiable que Kivy
- Kotlin + Gradle est le standard officiel Android — la documentation, les outils
  et la compatibilité sont bien mieux maintenus que python-for-android/buildozer.
- Compile directement avec `gradlew`, sans dépendances Python fragiles ni téléchargement
  de NDK/SDK Python.
- `minSdk = 21` et `targetSdk = 34` couvrent la quasi-totalité des téléphones Android
  encore en usage aujourd'hui.

## Limite technique à connaître
Sur Android 11 et plus, le déplacement de fichiers en dehors du dossier de l'app
nécessite la permission « Gérer tous les fichiers » (MANAGE_EXTERNAL_STORAGE), qui doit
être activée manuellement dans les réglages — c'est une contrainte imposée par Android
lui-même (stockage cloisonné), pas une limite de cette application.

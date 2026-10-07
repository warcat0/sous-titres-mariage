# 💍 Sous-titres Mariage

Appli Android gratuite : votre discours en français est sous-titré **en direct** en
anglais 🇬🇧 et en espagnol 🇪🇸, dans un bandeau flottant par-dessus une vidéo de fond.

* Reconnaissance vocale : celle d'Android (service Google, gratuit, nécessite internet).
* Traduction : Google ML Kit, gratuite, directement sur le téléphone (pas de clé, pas de compte).
* Aucun service payant.

---

## A. Obtenir l'APK (gratuit, ~10 minutes, sans rien installer)

1. Créez un compte gratuit sur **github.com** (si vous n'en avez pas).
2. Cliquez sur **+ → New repository**, nom : `sous-titres-mariage`, cochez **Public**, puis **Create repository**.
3. Sur la page du dépôt vide, cliquez sur **uploading an existing file**.
4. Décompressez le ZIP du projet sur votre ordinateur, ouvrez le dossier `SousTitresMariage`,
   sélectionnez **tout son contenu** (y compris le dossier `.github` — sur Mac : Cmd+Maj+.
   pour afficher les fichiers cachés ; sur Windows : Affichage → Éléments masqués) et
   glissez-le dans la page. Cliquez **Commit changes**.
5. Onglet **Actions** : la compilation démarre toute seule (≈ 5 min). Coche verte = c'est prêt.
6. Sur votre téléphone, ouvrez la page du dépôt → **Releases** → téléchargez
   `SousTitresMariage.apk` et ouvrez-le (autorisez « installer des applis inconnues »).

## B. Préparer le téléphone (une fois, avec internet)

Ouvrez l'appli et suivez les étapes 1 à 4 de l'écran :
1. Autoriser le micro, l'affichage par-dessus les autres applis et les notifications.
2. Télécharger les traductions (≈ 60 Mo, en Wi-Fi de préférence).
3. Choisir la vidéo de fond (MP4 conseillé).
4. Régler la taille du texte, puis tester la traduction avec une phrase.

## C. Le jour J

* **▶ Démarrer : vidéo de fond + sous-titres** : la vidéo passe en plein écran, en boucle,
  les sous-titres s'affichent par-dessus. C'est le mode le plus fiable.
* **▶ Démarrer les sous-titres seuls** : si vous préférez lire la vidéo avec une autre appli
  (Galerie, VLC…), les sous-titres restent par-dessus.
* La notification permet **Pause / Reprendre / Arrêter**.
* Le bandeau se déplace en le faisant glisser avec le doigt.

### Conseils importants
* **Répétez le discours en conditions réelles** (même micro, même vidéoprojecteur) quelques jours avant.
* Parlez posément, avec de petites pauses entre les phrases : la traduction devient plus juste
  à chaque fin de phrase.
* Un seul port USB‑C ? Il faudra un **hub USB‑C** (HDMI + entrée audio), ou projeter
  sans fil (Chromecast / Miracast) pour garder le port pour le micro.
* Mode avion **désactivé**, Wi‑Fi/4G actif, batterie chargée (ou branchée via le hub),
  **Ne pas déranger** activé pour éviter qu'une notification n'apparaisse à l'écran.
* L'option « Couper les bips » coupe aussi le son des médias pendant les sous-titres
  (décochez-la si vous voulez le son de la vidéo).
* Les noms propres (prénoms, lieux) peuvent être mal reconnus : testez-les et, si besoin,
  reformulez certaines phrases.

## Pour un développeur
Projet Android Studio standard (Kotlin, minSdk 26, targetSdk 35) :
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

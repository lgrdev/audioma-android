# Audioma

**Un lecteur audio Android simple, gratuit et sans publicité.**

Tu choisis un dossier, Audioma joue ta musique. C'est tout.

## Pourquoi Audioma ?

La plupart des lecteurs audio gratuits sont remplis de publicités, de bannières, d'achats intégrés et d'écrans inutiles. Audioma fait le contraire :

- **Gratuit et libre.** Code source ouvert sous licence MIT. Pas de version premium, pas d'achat intégré, pas d'abonnement.
- **Zéro publicité.** Aucune bannière, aucune vidéo, aucun pop-up. Jamais.
- **Simple.** Un seul écran : la liste de tes morceaux et un lecteur. Rien à configurer à part le dossier.
- **Respect de la vie privée.** Aucun compte, aucun pistage, aucune statistique. L'appli ne demande même pas l'accès à Internet : rien ne sort de ton téléphone.
- **Léger.** Moins de 2 Mo.

## Fonctionnalités

- 📁 **Dossier au choix** : tu choisis un dossier une fois, les sous-dossiers sont inclus automatiquement.
- 🔁 **Lecture continue** : à la fin de la liste, la lecture reprend au début.
- 🔀 **Lecture aléatoire** : un nouveau mélange à chaque tour, sans répéter un morceau avant que les autres soient passés.
- 🌙 **Lecture en arrière-plan** : la musique continue écran éteint, avec les commandes dans la notification, sur l'écran de verrouillage et sur les appareils Bluetooth.
- ⏯️ **Reprise automatique** : Audioma se souvient du dernier morceau, de la position et du mode aléatoire.
- 🎧 **Pause automatique** quand tu débranches ton casque.

Formats pris en charge : MP3, M4A, AAC, FLAC, OGG, Opus, WAV, etc.

## Installation

1. Télécharge le fichier `audioma-1.0.apk` depuis la [dernière version](https://github.com/lgrdev/audioma-android/releases/latest).
2. Ouvre-le sur ton téléphone et autorise l'installation depuis cette source si Android le demande.

Compatible Android 8.0 et plus récent.

## Compiler depuis les sources

Prérequis : JDK 17 et Android SDK (plateforme 35).

```bash
git clone git@github.com:lgrdev/audioma-android.git
cd audioma-android
echo "sdk.dir=/chemin/vers/android-sdk" > local.properties
./gradlew assembleRelease
```

L'APK est généré dans `app/build/outputs/apk/release/app-release.apk`.

## Technique

Kotlin, Jetpack Compose et Media3 (ExoPlayer). Le dossier est choisi via le sélecteur système d'Android : l'appli n'a pas besoin de l'autorisation d'accès à tout le stockage.

## Licence

[MIT](LICENSE) : libre d'utiliser, modifier et redistribuer.

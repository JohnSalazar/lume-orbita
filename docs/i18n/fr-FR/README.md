# lume órbita — guide d’utilisation et de contribution

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

Launcher original pour **Android TV et Google TV 11 ou version ultérieure**, avec Views natives en Kotlin. Aucun root ni firmware modifié. Le design et les fonctions restent identiques entre les langues.

## Langue
Ouvrez l’engrenage supérieur, puis **Langue**. Automatique suit la première langue compatible de la TV ; à défaut, l’anglais des États-Unis est utilisé. Le choix manuel persiste après redémarrage. Revenez à Automatique pour suivre le système. Seuls Lume et son économiseur d’écran changent : les paramètres d’Android, les autres applications et les titres ou descriptions des plateformes ne sont pas traduits.

21 paramètres régionaux : en-US, en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN, hi-IN, ar, bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK, mr-IN, de-DE, it-IT, ko-KR, id-ID et tr-TR. Chinois simplifié ; pendjabi occidental en Shahmukhi. L’arabe, l’ourdou et le pendjabi occidental inversent la disposition et la navigation horizontale gérée par l’application.

## Télécommande et utilisation quotidienne
- Les flèches déplacent le focus. OK ouvre un favori ; dans la bibliothèque, OK ajoute ou retire un favori. Maintenez OK pour déplacer, consulter les informations ou ouvrir la confirmation officielle de désinstallation.
- Reprendre la lecture et Suggestions changent l’aperçu lorsque leurs en-têtes reçoivent le focus. Gauche/droite parcourent les en-têtes et les commandes voisines ; bas ou OK ouvre la carte mémorisée. Contenus et images dépendent du partage des applications installées et de votre autorisation.
- L’ordre des favoris est conservé. Noms et icônes proviennent des applications installées. Installer des applications ouvre Google Play ; aucun APK n’est installé silencieusement.
- Réseau ouvre les paramètres d’Android. HDMI dépend du fabricant ; Input sur la télécommande reste une solution de secours.
- Cinéma assombrit et réchauffe le launcher et suspend les effets. Il ne modifie pas l’image de la TV. La programmation facultative de 20 h à 6 h suit l’heure locale de la TV.
- Ambiances propose rotation quotidienne et choix manuel. L’économiseur règle l’inactivité, l’intervalle, l’assombrissement et l’horloge. Les photos en ligne sont décodées en RAM, sans cache photo sur disque de l’application. Hors ligne, les paysages procéduraux alternent. Flèches, OK ou Retour ferment la vue. Android contrôle l’économiseur hors du launcher et la veille de la TV.

## Installer, mettre à jour et récupérer
Activez le débogage autorisé dans les paramètres officiels. Vérifiez le numéro de série exact avant chaque commande ADB ; remplacez DEVICE_SERIAL par l’appareil confirmé :

Avant et après `adb install -r`, notez et vérifiez le launcher par défaut sélectionné et la destination du bouton Home. Une mise à jour peut modifier ce choix sur certains appareils ; ne présumez pas qu’il sera conservé. Gardez un autre launcher fonctionnel disponible et rétablissez votre choix avec le sélecteur Android si nécessaire.

```sh
adb devices
adb -s DEVICE_SERIAL install -r app-release.apk
```

Pour préserver les préférences, les mises à jour doivent conserver le package et le certificat de signature. Gardez la clé privée. Un autre certificat ne peut pas mettre à jour l’installation existante. N’effacez pas les données pour changer de langue.

Choisissez Lume comme accueil uniquement avec le sélecteur officiel et si le fabricant le permet. Testez Home depuis une autre application et la sortie de veille séparément. Avant de désinstaller le launcher par défaut, installez et sélectionnez un autre launcher compatible, puis vérifiez Home. Supprimez ensuite Lume dans les paramètres d’Android. Aucun root, changement de firmware ou retrait de composants système n’est nécessaire. Le comportement varie selon le fabricant.

## Compilation, tests et licences
JDK 17, Android SDK Platform 35 et Build Tools 36.0.0 sont requis. Configurez JAVA_HOME et ANDROID_HOME. Depuis la racine du projet, générez les ressources avant les vérifications et la compilation :

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Sous Windows, utilisez gradlew.bat. APK : app/build/outputs/apk/release/app-release.apk. Depuis la racine du projet :

```sh
python tools/verify_locales.py
```

Les traductions se trouvent dans les catalogues common/activity et tools/translations. Régénérez les ressources après modification, en conservant les IDs et paramètres de formatage. LocaleSmokeInstrumentation est un lanceur de tests uniquement ; il ne change ni la langue globale ni Home. Utilisez un appareil ou émulateur dédié. Un AVD de téléphone ne certifie pas les intégrations TV ni la télécommande physique.

Code original sous MIT, copyright 2026 Joao Salazar. Consultez LICENSE et THIRD_PARTY_NOTICES.md. Photos, marques et contenus tiers conservent leurs droits ; auteurs et liens de licence restent inchangés. Les comptes privés de streaming ne sont ni consultés ni traduits. Compatibilité, polices et performances nécessitent des tests par appareil.

La relecture par des locuteurs natifs et le QA par langue restent nécessaires. Résultats réels dans docs/qa ; ce guide ne prétend pas que tous les tests ont été effectués. Installation : docs/installation.md. Compilation et signature : docs/build.md.

## Mises à jour depuis GitHub (0.4.3+)

Dans **À propos**, choisissez **Rechercher des mises à jour** : GitHub est consulté uniquement après votre clic, sans vérifications périodiques. Si une version compatible plus récente est disponible, choisissez **Télécharger la mise à jour**. Après vérification du téléchargement, choisissez **Installer la mise à jour** ; Android vous demande ensuite de confirmer l'installation. Si Android exige une autorisation, ouvrez **Autoriser cette source** et autorisez vous-même Lume dans les paramètres Android, puis revenez confirmer. Lume n'accorde pas cet accès automatiquement. Une mise à jour avec le même paquet et le même certificat de signature conserve les paramètres ; ne désinstallez pas Lume pour le mettre à jour. Vérifiez ensuite Home. Si la TV change ce choix, **Vérifier l'écran d'accueil** ouvre les paramètres officiels pour vous permettre de le sélectionner manuellement.

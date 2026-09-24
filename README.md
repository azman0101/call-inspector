# Arcep Opérateur

Application Android Kotlin/Jetpack Compose qui lit le journal d'appels du téléphone et rapproche les numéros des ressources en numérotation contenues dans une base SQLite embarquée.

## Fonctionnalités

- Historique des appels Android, filtres et recherche. L'application demande l'autorisation `READ_CALL_LOG` à l'exécution; sans cette autorisation, elle propose des appels de démonstration.
- Recherche d'un numéro ou d'un préfixe dans la base ARCEP locale.
- Affichage de la tranche, de l'opérateur attributaire et des informations disponibles sur cet opérateur.
- Favoris, indicateur de démarchage/indésirable et notes locales associés aux numéros.
- Statistiques calculées à partir de la base embarquée et des appels chargés.

## Construire l'application

### Prérequis

- JDK 21 (version utilisée par le script de préparation Termux du dépôt).
- Android SDK avec la plateforme Android 36, extension 1, et Build Tools 36.0.0. Le `compileSdk` du module `app` demande précisément API 36 / extension 1.
- `ANDROID_HOME` ou `ANDROID_SDK_ROOT` pointant vers ce SDK, ou un `sdk.dir` local configuré dans `local.properties`.
- Un accès réseau lors du premier build pour télécharger Gradle et les dépendances Maven.

Sur macOS, installer ces composants dans **Android Studio > Settings > Languages & Frameworks > Android SDK** (sur macOS, aussi accessible via **Tools > SDK Manager**). Le chemin SDK standard est `~/Library/Android/sdk`; Android Studio peut le fournir à Gradle via `local.properties`. Le wrapper Gradle du dépôt utilise Gradle 9.6.0. Une fois le SDK installé, depuis un terminal macOS/Linux à la racine du dépôt :

```sh
./gradlew --version
./gradlew assembleDebug
```

L'APK debug est créé dans `app/build/outputs/apk/debug/app-debug.apk`. Pour l'installer sur un appareil connecté à ADB :

```sh
./gradlew installDebug
```

ou :

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Au premier lancement, accorder à l'application l'accès au journal d'appels si l'historique réel est voulu. Le build debug utilise la clé debug Android standard de la machine; aucune clé locale ignorée par Git n'est requise.

### Préparer un environnement Termux

`setup.sh` est réservé à Termux sur Android ARM64. Il installe/configure Java 21, le SDK Android API 36, Gradle 9.6.0 et l'outil AAPT2 adapté à Termux. Il met également à jour les paquets et accepte les licences du SDK. Depuis la racine du dépôt :

```sh
bash setup.sh
./gradlew assembleDebug
```

Sur les autres systèmes, utiliser Android Studio ou installer les prérequis ci-dessus. Le chemin AAPT2 spécifique à Termux est configuré par le script dans les propriétés Gradle de l'utilisateur, pas dans la configuration partagée du projet.

### Build release

Le build release requiert un keystore privé et les mots de passe fournis dans l'environnement. L'alias attendu par `app/build.gradle.kts` est `upload`.

```sh
export KEYSTORE_PATH=/chemin/vers/keystore.jks
export STORE_PASSWORD='…'
export KEY_PASSWORD='…'
./gradlew assembleRelease
```

Ne pas ajouter le keystore ni ses secrets au dépôt.

## Données ARCEP et API

### Ce que fait l'application

La recherche ARCEP est effectuée **localement**. Le fichier [arcep_data.db](app/src/main/assets/arcep_data.db) est copié dans le stockage privé de l'application, puis interrogé avec SQLite. Le code ne consomme pas d'API HTTP ARCEP et ne synchronise pas cette base à l'exécution. Les boutons ARCEP ouvrent des pages web dans le navigateur; ce ne sont pas des appels d'API depuis l'application.

La base contient actuellement les tables suivantes :

| Table | Informations utilisées |
| --- | --- |
| `number_ranges` | Préfixe `ezabpqm`, bornes de tranche, code et nom de l'opérateur attributaire, territoire et date d'attribution |
| `operators` | Code et nom de l'opérateur, SIRET, RCS, adresse et date de déclaration lorsqu'ils sont renseignés |
| `call_notes` | Table créée par l'application pour les favoris, indicateurs et notes utilisateur; ce ne sont pas des données ARCEP |

Au moment de cet audit, le fichier embarqué contenait 20 655 tranches et 1 592 opérateurs. Les dates d'attribution présentes vont du 01/01/2015 au 31/10/2025. La date « Septembre 2026 » affichée dans les statistiques est une valeur codée en dur, pas une date de génération vérifiée dans la base. Le dépôt ne comprend pas de script d'import ou de mise à jour des données; la fraîcheur du fichier doit donc être contrôlée avant publication.

### Source officielle et actualisation

L'ARCEP publie les ressources de numérotation attribuées dans son fichier **MAJNUM**, ainsi que des fichiers décrivant les opérateurs attributaires. Le portail public liste notamment le [CSV MAJNUM](https://extranet.arcep.fr/uploads/MAJNUM.csv) et le [CSV des identifiants CE](https://extranet.arcep.fr/uploads/identifiants_CE.csv); au moment de l'audit, il indiquait une mise à jour de MAJNUM au 15/09/2026. La [spécification des fichiers](https://extranet.arcep.fr/uploads/spec_export_num_arcep.pdf) décrit les colonnes et précise que les exports sont des états complets, mis à jour après les réunions du collège. Le dépôt ne télécharge ni ne transforme automatiquement ces fichiers en `arcep_data.db`.

Pour actualiser la base, récupérer les exports publics à jour, vérifier leur schéma et leur date de publication, transformer les colonnes vers le schéma SQLite du projet, valider le résultat, puis remplacer `app/src/main/assets/arcep_data.db`. Une automatisation nécessiterait un importeur explicite; l'application ne possède actuellement ni endpoint d'API ni mécanisme de synchronisation.

### Limites d'interprétation

- La recherche identifie l'opérateur auquel l'ARCEP a attribué la tranche. Elle ne prouve pas que cet opérateur dessert actuellement l'abonné : les numéros peuvent être portés. L'ARCEP indique ne pas disposer des informations sur les numéros portés; voir [La numérotation – ARCEP](https://www.arcep.fr/la-regulation/grands-dossiers-thematiques-transverses/la-numerotation.html).
- Un préfixe réglementé pour les appels automatisés n'est pas, à lui seul, une preuve d'appel frauduleux ou indésirable. L'indicateur de démarchage de l'application est une heuristique locale.
- Les libellés régionaux associés aux préfixes 01–05 sont à revoir : depuis le 1er janvier 2023, les numéros fixes 01–05 ne sont plus limités à leur ancienne zone géographique.

## Audit technique

Points relevés par lecture statique du dépôt (24 septembre 2026) :

1. **Classification Outre-mer à corriger.** Dans `PhoneNumberType.classify`, les tests génériques `01`–`07` précèdent les tests `059`, `026` et `069`; ces derniers ne sont donc jamais atteints. Les numéros ultramarins normalisés peuvent recevoir une catégorie métropolitaine ou mobile erronée.
2. **Catégorie « démarchage / spam » trop affirmative.** Les préfixes réglementés signalent un usage possible/réservé aux appels automatisés selon le plan de numérotation; ils ne classent pas le contenu ou la légitimité de l'appel. L'interface devrait distinguer la catégorie réglementaire du signalement utilisateur.
3. **Fraîcheur de la base non traçable.** La date affichée est codée en dur et le dépôt n'a ni manifeste de provenance ni procédure de mise à jour. La date d'attribution la plus récente trouvée dans le fichier est 31/10/2025.
4. **Sauvegarde Android à décider.** Le manifeste active `allowBackup`; les fichiers de règles de sauvegarde sont encore des modèles sans exclusions effectives. Les notes et indicateurs utilisateur stockés dans la base privée peuvent ainsi être inclus dans les sauvegardes Android selon l'appareil et sa configuration. Décider explicitement si ces données doivent être sauvegardées.
5. **Dépendances réseau non utilisées dans le code applicatif audité.** Retrofit, OkHttp et Firebase AI sont déclarés dans Gradle, mais aucune intégration correspondante n'a été trouvée dans le code source. L'application déclare aussi `INTERNET`; vérifier si ces éléments de scaffold sont nécessaires avant une publication.

La configuration de build a été rendue portable pendant cet audit : Gradle utilise désormais la signature debug standard de l'Android Gradle Plugin, et le chemin AAPT2 Termux n'est plus codé dans `gradle.properties` du projet.

## Structure du projet

- `app/src/main/java/com/example/data/db/` : copie, interrogation et notes dans SQLite.
- `app/src/main/java/com/example/data/repository/` : lecture du journal d'appels Android.
- `app/src/main/java/com/example/viewmodel/` : état d'écran, filtres et recherche.
- `app/src/main/java/com/example/ui/` : écrans et composants Compose.
- `app/src/main/assets/arcep_data.db` : base de données embarquée.
- `setup.sh` : préparation de l'environnement de build Termux ARM64.

# Arcep Opérateur

Application Android Kotlin / Jetpack Compose qui analyse l'historique d'appels du téléphone et identifie automatiquement l'opérateur de télécommunication et l'entreprise titulaire de chaque numéro selon la base officielle de l'**ARCEP** (Autorité de régulation des communications électroniques, des postes et de la distribution de la presse).

---

## Sommaire

1. [Fonctionnalités](#fonctionnalités)
2. [Choix d'implémentation et d'architecture](#choix-dimplémentation-et-darchitecture)
3. [Corrections récentes apportées au projet](#corrections-récentes-apportées-au-projet)
4. [Données ARCEP et fonctionnement de la base locale](#données-arcep-et-fonctionnement-de-la-base-locale)
5. [Construire et tester l'application](#construire-et-tester-lapplication)
6. [Structure du projet](#structure-du-projet)
7. [TODO & Pistes d'évolution](#todo--pistes-dévolution)

---

## Fonctionnalités

- **Journal d'appels enrichi (Call History)** :
  - Lecture des appels entrants, sortants, manqués et rejetés avec date, heure et durée.
  - Résolution instantanée de l'opérateur attributaire (Orange, SFR, Free, Bouygues Telecom, Manifone, OVH, BJT Partners, etc.).
  - Filtres rapides : *Tous*, *⚠️ Démarchage / Spam*, *Manqués*, *Entrants*, *Favoris*.
  - Recherche en texte intégral par numéro, nom de contact ou nom d'opérateur.
- **Dossier légal ARCEP complet** :
  - Identité officielle de l'entreprise (dénomination sociale).
  - Identifiants légaux : numéro SIRET et Registre du Commerce (RCS).
  - Siège social et adresse déclarée auprès du régulateur.
  - Plage de numérotation complète (tranche début à tranche fin), territoire et date de décision d'attribution.
  - Raccourci vers la fiche officielle : bouton direct ouvrant `arcep.fr` avec copie automatique du numéro dans le presse-papiers.
- **Détection du démarchage commercial réglementé** :
  - Reconnaissance automatique des préfixes réservés aux centres d'appels et télévendeurs depuis le 1er janvier 2023 (Décision ARCEP n° 2022-1583).
- **Recherche manuelle & Préfixes** :
  - Saisie libre d'un numéro à 10 chiffres ou d'un préfixe (ex. `01 62`, `06 12`, `07 81`, `08 92`).
  - Bouton pour coller directement depuis le presse-papiers.
  - Exploration de toutes les tranches attribuées correspondantes.
- **Gestion des annotations locales** :
  - Marquage de numéros en favoris ou comme indésirables / démarchage.
  - Ajout de notes personnelles locales associées aux numéros.
- **Observatoire et statistiques** :
  - Métriques globales de la base ARCEP (20 655 tranches répertoriées, 1 592 opérateurs déclarés).
  - Répartition des appels de l'utilisateur par opérateur et volume d'appels de téléprospection.
  - Guide récapitulatif du cadre légal (Loi Naegelen, interdiction du démarchage en 06/07, protocole MAN).

---

## Choix d'implémentation et d'architecture

### 1. Base SQLite locale embarquée (Offline-First & Privacy-by-Design)
* **Pourquoi pas d'appels HTTP ou de scraping en direct sur `arcep.fr` ?**
  - La page web de consultation de l'ARCEP (`identifier-un-operateur-par-un-numero.html`) intègre un mécanisme de protection anti-bot (WAF / challenge visuel / captcha) qui bloque tout scraping programmatique ou requête automatisée.
  - **Confidentialité absolue** : Le journal d'appels d'un utilisateur contient des données éminemment personnelles. En embarquant la base complète dans `app/src/main/assets/arcep_data.db` (2.95 Mo), **aucun numéro de téléphone n'est envoyé à l'extérieur**. L'intégralité du traitement se fait en local sur le téléphone.
  - **Performance** : La recherche s'exécute en moins de 2 millisecondes via des index SQLite optimisés (`idx_tranche`, `idx_ezabpqm`), sans dépendance au réseau ni risque d'erreur 429 / quota.

### 2. Lien interactif avec la page officielle de l'ARCEP
Pour répondre au besoin de vérification sur le site web officiel mentionné dans la demande initiale :
- Chaque fiche d'appel ou résultat de recherche propose le bouton **« Vérifier sur la page officielle ARCEP »**.
- Ce bouton copie automatiquement le numéro normalisé dans le presse-papiers du smartphone et lance le navigateur vers `https://www.arcep.fr/mes-demarches-et-services/entreprises/fiches-pratiques/identifier-un-operateur-par-un-numero.html` pour que l'utilisateur puisse coller et corroborer l'information en un geste.

### 3. Gestion transparente des autorisations Android (`READ_CALL_LOG`)
L'autorisation d'accès au journal d'appels est une permission critique (*Dangerous Permission*) soumise à des règles strictes sur Google Play :
- **Demande à l'exécution avec Jetpack Compose** via `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.
- **Mode Démo non bloquant** : Si l'utilisateur n'a pas encore accordé la permission (ou s'il exécute l'application sur un émulateur sans historique réel), l'application propose immédiatement un jeu de 10 appels français types (appels mobiles, fixes régionaux, télémarketing 0162/0270, services spéciaux).
- **Dialogue d'information et de confidentialité (`PermissionRationaleDialog`)** : Présente clairement les garanties (traitement local, absence de partage tiers, utilité anti-spam).
- **Gestion des refus persistants (`isPermanentlyDenied`)** : Détection des refus répétés avec bouton ouvrant directement les paramètres de l'application (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
- **Synchronisation automatique** dans `MainActivity.onResume()` dès que la permission est activée.

### 4. Réglementation française anti-démarchage (ARCEP 2022-1583)
Depuis le 1er janvier 2023, la législation française encadre strictement le télémarketing :
- Préfixes obligatoires réservés au démarchage :
  - Région parisienne : `0162`, `0163`
  - Nord-Ouest : `0270`, `0271`
  - Nord-Est : `0377`, `0378`
  - Sud-Est : `0424`, `0425`
  - Sud-Ouest : `0568`, `0569`
  - Numéros polyvalents nationaux : `0948`, `0949`
  - Outre-mer : `09475` à `09482`
- Interdiction formelle du démarchage à partir de numéros mobiles (06 / 07).
- L'application met en évidence ces plages avec un badge distinctif et indique quel opérateur télécom héberge la ligne du centre d'appels.

---

## Corrections récentes apportées au projet

Plusieurs ajustements ciblés ont été réalisés pour assurer le bon fonctionnement sur appareil réel et la portabilité des builds :

1. **Correction de la lecture du `CallLog` Android (`CallLogRepository.kt`)** :
   - *Problème* : L'ajout d'une clause `LIMIT 100` dans le paramètre `sortOrder` de `ContentResolver.query` déclenchait une `IllegalArgumentException` ou un plantage SQLite sur certains appareils et versions d'Android ne tolérant pas de syntaxe SQL non standard dans le provider d'appels.
   - *Correction* : Utilisation d'un tri pur `CallLog.Calls.DATE DESC`, avec arrêt de lecture plafonné programmatiquement (`while (cursor.moveToNext() && entries.size < 100)`), et ajout d'un log d'erreur explicite (`Log.e`).
2. **Correction de la classification Outre-mer (`PhoneNumberType.kt`)** :
   - *Problème* : Les vérifications génériques `startsWith("05")`, `startsWith("02")` et `startsWith("06")` intervenaient avant les tests ultramarins, rendant la détection des numéros des DOM (`0590`, `0594`, `0596`, `0262`, `0690`, `0692`, etc.) inaccessible.
   - *Correction* : Réorganisation de l'ordre d'évaluation pour prioriser les indicatifs d'Outre-mer avant les plages métropolitaines.
3. **Portabilité de la configuration Gradle (`gradle.properties`)** :
   - *Problème* : La présence de `android.aapt2FromMavenOverride=/data/data/com.termux/...` dans le `gradle.properties` versionné du projet cassait la compilation sur toutes les machines non-Termux (environnement cloud, macOS, Linux, machines de build).
   - *Correction* : Retrait de cet override du fichier partagé du dépôt. Le script `setup.sh` l'injecte de façon isolée dans `~/.gradle/gradle.properties` sur les environnements Termux ARM64 sans impacter les autres développeurs.
4. **Mise à niveau du wrapper Gradle** :
   - Mise à jour vers Gradle 9.6.0 (`gradle-wrapper.properties` et binaire wrapper associé).

---

## Données ARCEP et fonctionnement de la base locale

### Tables SQLite utilisées

Le fichier `app/src/main/assets/arcep_data.db` est copié au premier démarrage dans le répertoire privé de l'application (`context.getDatabasePath("arcep_data.db")`).

| Table | Rôle & Contenu |
| --- | --- |
| `number_ranges` | Contient les 20 655 tranches de numérotation attribuées par l'ARCEP (colonnes `ezabpqm`, `tranche_debut`, `tranche_fin`, `operator_code`, `operator_name`, `territory`, `attribution_date`). Indexée sur `(tranche_debut, tranche_fin)` et `ezabpqm`. |
| `operators` | Contient les 1 592 opérateurs déclarés avec leurs identifiants légaux (`code`, `name`, `siret`, `rcs`, `address`, `declaration_date`). |
| `call_notes` | Table locale gérée par l'application pour stocker les drapeaux utilisateur (favori, numéro signalé comme spam, note textuelle personnalisée). |

### Algorithme de résolution d'un numéro
1. **Normalisation** : Suppression des espaces, tirets et conversion des formats internationaux (`+33` ou `0033` $\to$ `0`).
2. **Recherche par encadrement de tranche** :
   ```sql
   SELECT * FROM number_ranges 
   WHERE tranche_debut <= :normalized AND tranche_fin >= :normalized 
   ORDER BY LENGTH(ezabpqm) DESC LIMIT 1;
   ```
3. **Recherche de repli par préfixe (EZABPQM)** : Analyse progressive des préfixes de longueur 7, 6, 5, 4, 3 si le numéro est partiel ou court.
4. **Jointure avec l'opérateur** : Récupération des données administratives dans `operators` via `operator_code`.

### Limites d'interprétation légales
- **Portabilité des numéros** : L'ARCEP attribue des tranches initiales aux opérateurs. Un abonné particulier ou professionnel peut avoir porté son numéro chez un autre opérateur. L'ARCEP ne publie pas la base en temps réel des numéros portés (gérée par l'APNF entre opérateurs). La recherche identifie donc l'opérateur attributaire de la ressource.
- **Nature du démarchage** : L'appartenance d'un numéro à une tranche légale de télémarketing (ex. `0162`) indique un usage réglementaire réservé aux appels de prospection commerciale par automate ou centre d'appels, mais ne préjuge pas à elle seule de la légalité ou du caractère frauduleux d'une société donnée.

---

## Construire et tester l'application

### Prérequis
- **JDK 21**
- **Android SDK** avec `platforms;android-36` (extension 1) et `build-tools;36.0.0`
- Variables `ANDROID_HOME` ou `ANDROID_SDK_ROOT` configurées (ou chemin renseigné dans `local.properties`).

### Compilation sur macOS / Linux
```sh
# Vérifier la version de Gradle
./gradlew --version

# Compiler l'application en mode Debug
./gradlew assembleDebug

# Exécuter les tests unitaires et Robolectric
./gradlew testDebugUnitTest
```

L'APK généré se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

### Déploiement sur appareil ou émulateur via ADB
```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Environnement spécifique Termux (Android ARM64)
Le fichier `setup.sh` à la racine automatise la préparation d'un environnement autonome sous Termux ARM64 :
```sh
bash setup.sh
./gradlew assembleDebug
```

---

## Structure du projet

```text
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Déclaration des permissions READ_CALL_LOG, INTERNET
│   │   ├── assets/
│   │   │   └── arcep_data.db            # Base SQLite officielle ARCEP pré-indexée (2.95 Mo)
│   │   ├── java/com/example/
│   │   │   ├── MainActivity.kt          # Point d'entrée, initialisation ViewModel & cycle de vie
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   └── ArcepDatabaseManager.kt # Gestionnaire SQLite, requêtes indexées, notes
│   │   │   │   ├── model/
│   │   │   │   │   ├── ArcepModels.kt   # ArcepOperator, ArcepNumberRange
│   │   │   │   │   ├── CallLogEntry.kt  # Modèle d'appel, CallType, ArcepLookupResult
│   │   │   │   │   └── PhoneNumberType.kt # Catégorisation & préfixes légaux de démarchage
│   │   │   │   └── repository/
│   │   │   │       └── CallLogRepository.kt    # Lecture CallLog Android & données de démo
│   │   │   ├── ui/
│   │   │   │   ├── MainScreen.kt        # Navigation M3 (Journal, Recherche, Observatoire)
│   │   │   │   ├── components/
│   │   │   │   │   ├── CallItemCard.kt  # Carte d'appel avec badges opérateur & statut
│   │   │   │   │   ├── CallDetailModal.kt # Dossier complet ARCEP & lien officiel
│   │   │   │   │   ├── OperatorBadge.kt # Badges visuels distinctifs
│   │   │   │   │   └── PermissionRationaleDialog.kt # Dialogue explicatif des autorisations
│   │   │   │   └── screens/
│   │   │   │       ├── CallHistoryScreen.kt    # Écran principal avec filtres et recherche
│   │   │   │       ├── NumberLookupScreen.kt   # Outil de recherche manuelle et préfixes
│   │   │   │       └── StatsAndInfoScreen.kt   # Observatoire, métriques et cadre réglementaire
│   │   │   └── viewmodel/
│   │   │       └── ArcepViewModel.kt    # Gestion de l'état UI, filtres, permissions, recherche
│   │   └── res/                         # Ressources graphiques, icônes adaptatives, chaînes
├── gradle/                              # Configuration du wrapper Gradle 9.6.0
├── setup.sh                             # Script de provisionnement Termux ARM64
└── README.md
```

---

## TODO & Pistes d'évolution

### Comment la base de données de l'ARCEP est-elle maintenue à jour ?

La base embarquée actuelle est un instantané compilé à partir des exports officiels de l'ARCEP :
- **Fichier des tranches** : [`MAJNUM.csv`](https://extranet.arcep.fr/uploads/MAJNUM.csv) (mis à jour après chaque réunion du collège de l'ARCEP).
- **Fichier des identifiants d'opérateurs** : [`identifiants_CE.csv`](https://extranet.arcep.fr/uploads/identifiants_CE.csv) (mis à jour chaque semaine).

Pour assurer la pérennité et la fraîcheur des données, les chantiers suivants sont identifiés :

1. **Script d'import automatisé dans le dépôt (`tools/update_arcep_db.py`)** :
   - Créer un script Python dédié prenant en charge le téléchargement direct des deux CSV officiels, la vérification de leur intégrité et de leur encodage (`latin1` / ANSI vers UTF-8), la jointure sur le code opérateur (`Mnémo` $\leftrightarrow$ `CODE_OPERATEUR`), et la régénération de `app/src/main/assets/arcep_data.db` avec `VACUUM` et indexation B-Tree.
   - Intégrer l'exécution de ce script dans un workflow GitHub Actions mensuel ou déclenché avant chaque release de l'application.

2. **Mise à jour dynamique OTA (Over-The-Air) dans l'application** :
   - Mettre en place un service d'arrière-plan via `WorkManager` (déclenché périodiquement lorsque l'appareil est connecté en Wi-Fi et en charge).
   - Ce service interrogerait un endpoint miroir léger ou vérifierait les en-têtes HTTP `Last-Modified` / `ETag` des exports data.gouv.fr / extranet ARCEP.
   - En cas de nouvelle version, téléchargement d'un fichier compressé (gzip/zstd) et remplacement sécurisé de la base SQLite locale dans le dossier de l'application, sans nécessiter la publication d'une nouvelle version de l'APK.

3. **Traçabilité de la version de la base** :
   - Remplacer la date statique affichée dans l'observatoire par une métadonnée injectée dynamiquement lors de la génération de la base (ex. table `arcep_metadata` contenant le timestamp précis de l'export source, le SHA-256 des CSV sources et le nombre exact de tranches).

4. **Optimisation des dépendances** :
   - Nettoyer les dépendances inutilisées déclarées dans `app/build.gradle.kts` (scaffold Firebase AI, Retrofit/OkHttp non sollicités par la base locale) afin de réduire davantage la taille de l'APK final.

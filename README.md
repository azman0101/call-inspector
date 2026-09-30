# Info Opérateur

[![Build](https://github.com/azman0101/call-inspector/actions/workflows/build_apk.yml/badge.svg?branch=main&event=push)](https://github.com/azman0101/call-inspector/actions/workflows/build_apk.yml?query=branch%3Amain+event%3Apush)
[![VirusTotal](https://img.shields.io/badge/VirusTotal-rapport%20de%20l%27APK%20release-394EFF?logo=virustotal&logoColor=white)](https://www.virustotal.com/gui/file/e2327021f482d577823a1b2cc5cf0dddcbf445e53213f73521c399406926c171)
[![Dernière release](https://img.shields.io/github/v/release/azman0101/call-inspector?label=t%C3%A9l%C3%A9charger%20l%27APK)](https://github.com/azman0101/call-inspector/releases/latest)
[![Licence MIT](https://img.shields.io/badge/licence-MIT-green)](LICENSE)

Application Android Kotlin / Jetpack Compose qui analyse l'historique d'appels du téléphone et identifie automatiquement l'opérateur de télécommunication et l'entreprise titulaire de chaque numéro selon les données publiques de l'**ARCEP** (Autorité de régulation des communications électroniques, des postes et de la distribution de la presse).

> **Note sur le nom et la marque pour publication** : Initialement nommé « Arcep Opérateur », le projet a été renommé **« Info Opérateur »** pour respecter le droit des marques de l'ARCEP et les règles de publication du Google Play Store interdisant l'usage de marques institutionnelles comme nom d'application.

> [!NOTE]
> **Code largement produit par IA.** L'essentiel de ce dépôt (code Kotlin, tests, workflows CI, scripts et documentation) a été écrit par des assistants de programmation, sous la direction et la relecture du mainteneur :
>
> - le projet a démarré dans Google AI Studio (d'où l'ancien identifiant `com.aistudio.operatorlookup`) ;
> - Claude Code (Anthropic), Google Jules et l'agent GitHub Copilot ont ensuite produit une grande partie des commits et des pull requests (les commits des agents portent leur nom ou un trailer `Co-Authored-By`).
>
> La plupart des changements passent par une pull request et la CI (tests unitaires et Robolectric, scan OWASP, GitGuardian), et le mainteneur décide des fusions. Le code n'a pas eu d'audit indépendant : relisez-le avant de le réutiliser.
>
> **Coût d'une session.** À titre indicatif, une seule session Claude Code, du 25 au 27 septembre 2026 (dont les PR #29 à #35 : chiffrement des notes, CGU, accélération de la CI, durcissement avant publication), a demandé environ 1 000 réponses du modèle :
>
> | Tokens | Volume |
> |---|---:|
> | Lus depuis le cache de prompt | 387 millions |
> | Écrits dans le cache | 3,0 millions |
> | Générés | 0,74 million |
> | Entrée hors cache | ~2 000 |
>
> Au tarif public de l'API Anthropic, cela représente **environ 115 $**, dont deux tiers pour les lectures en cache, qui sont facturées ~20 fois moins cher qu'une entrée normale. C'est un équivalent au tarif public : le montant réellement facturé dépend du mode d'accès (abonnement ou API). Les autres sessions et agents ne sont pas comptés.

---

## Sommaire

1. [Fonctionnalités](#fonctionnalités)
2. [Choix d'implémentation et d'architecture](#choix-dimplémentation-et-darchitecture)
3. [Corrections récentes apportées au projet](#corrections-récentes-apportées-au-projet)
4. [Données ARCEP et fonctionnement de la base locale](#données-arcep-et-fonctionnement-de-la-base-locale)
5. [Construire et tester l'application](#construire-et-tester-lapplication)
6. [Structure du projet](#structure-du-projet)
7. [TODO & Pistes d'évolution](#todo--pistes-dévolution)
8. [Licence](#licence)

---

## Fonctionnalités

- **Journal d'appels enrichi (Call History)** :
  - Lecture des appels reçus (entrants, manqués, rejetés et bloqués) avec date, heure et durée ; les appels sortants sont ignorés.
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
- **Signalement assisté sur SignalConso** :
  - Depuis la fiche d'un appel reçu, le bouton « Signaler ce démarchage (SignalConso) » ouvre le formulaire officiel de la DGCCRF (« Démarchage abusif ») dans l'application.
  - Préremplissage à partir du journal d'appels : numéro appelant, dates des appels, description et motif. Le motif est choisi, par ordre de priorité, d'après :
    - le sujet de l'appel, repéré dans la note de l'utilisateur ou le nom de l'appelant : administration usurpée, rénovation énergétique, CPF ;
    - ce que montre l'appel lui-même : au moins 5 appels en 30 jours, week-end ou jour férié, hors horaires autorisés ;
    - un refus de démarchage datant de moins de 60 jours, s'il est mentionné dans la note ;
    - un numéro en 06/07 ;
    - à défaut, « refus de démarchage datant de moins de 60 jours » : signaler l'appel revient à le tenir pour illicite. Un bandeau demande de vérifier ce motif, qui n'est pas repris dans la description.
  - Entreprise signalée : le nom de l'appelant s'il figure dans le journal d'appels, sinon l'opérateur auquel l'ARCEP a attribué le numéro (recherche par SIRET, résultat sélectionné s'il est unique ; recherche par nom à défaut). Un bandeau explique ce choix et l'utilisateur peut le changer.
  - Coordonnées (étape 4 : identité, email, téléphone, numéro de référence, choix de partage avec l'entreprise) mémorisables via « Mes coordonnées » : stockées dans une base locale dédiée (`reporter_profile_secure.db`) chiffrée par SQLCipher, avec une clé protégée par l'Android Keystore (StrongBox si disponible), exclue des sauvegardes et des transferts d'appareil ; elles ne quittent le téléphone que dans un signalement validé par l'utilisateur.
  - L'utilisateur vérifie chaque étape, choisit l'entreprise si besoin et valide lui-même l'envoi.
  - Signalements envoyés comptabilisés par numéro, quand SignalConso affiche son accusé de réception : l'historique indique le nombre de signalements et la date du dernier, rappelés sous le bouton de signalement. Ils sont stockés dans la même base chiffrée.
- **Gestion des annotations locales** :
  - Marquage de numéros en favoris ou comme indésirables / démarchage.
  - Ajout de notes personnelles locales associées aux numéros, chiffrées sur le téléphone (SQLCipher, clé Android Keystore).
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
- Chaque fiche d'appel ou résultat de recherche propose le bouton **« Consulter le portail officiel (arcep.fr) »**.
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

### 5. Confidentialité et conditions d'utilisation
Le texte de référence est [`legal/CGU.md`](legal/CGU.md) : l'application affiche ce même fichier et demande de l'accepter à la première ouverture et à chaque modification. En bref :
- Journal d'appels, notes, favoris et recherches sont traités sur le téléphone. Seul un signalement SignalConso validé par l'utilisateur transmet des données (au service public SignalConso).
- Pas de serveur propre : l'application se connecte à l'extranet de l'ARCEP pour la mise à jour, à SignalConso pour les signalements, et à Sentry seulement si l'utilisateur l'active.
- Rapports d'anomalies désactivés par défaut. S'ils sont activés : données techniques pseudonymes (identifiant d'installation aléatoire), sans numéro, contact, note, nom ni email.

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
| `call_notes` | Ancienne table des notes, favoris et drapeaux spam, désormais dans la base chiffrée `user_notes_secure.db` : les notes existantes y sont migrées au premier lancement, puis effacées d'`arcep_data.db`. |

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

### Compilation automatisée et téléchargement d'APK de test (GitHub Actions)
Un workflow GitHub Actions dédié (`.github/workflows/build_apk.yml`) est configuré pour compiler et tester automatiquement l'application :
- **Déclencheurs** :
  - À chaque `push` ou `pull_request` sur les branches principales.
  - Déclenchement manuel via **Actions > Build Test APK > Run workflow** (avec option pour exécuter ou ignorer les tests unitaires Robolectric).
- **Artefact généré** :
  - L'APK de test signé avec la clé de debug (`app-debug.apk`) est automatiquement téléchargeable sous le nom **`info-operateur-debug-apk`** dans la section *Artifacts* de l'exécution GitHub Actions (durée de rétention : 14 jours).
  - Cet APK peut être installé directement sur n'importe quel smartphone Android de test sans certificat de production.

### Releases : télécharger l'APK signé

Chaque build sur `main` publie une [release GitHub](https://github.com/azman0101/call-inspector/releases) (`tools/publish_release.sh`) :
- un tag `v1.0.<numéro du run>` sur le commit construit ;
- l'APK signé avec la clé de release, `info-operateur-<version>.apk`, et son fichier `.sha256` ;
- des notes de version : empreinte SHA-256 de l'APK, certificat de signature, rapport VirusTotal, lien vers le run GitHub Actions et son artefact, puis les pull requests fusionnées depuis la release précédente.

La [dernière release](https://github.com/azman0101/call-inspector/releases/latest) est toujours à la même adresse. La CI refuse de publier un APK qui n'est pas signé avec la clé du projet (secret `KEYSTORE_BASE64` absent, par exemple) : il ne pourrait mettre à jour aucune installation. Relancer le job d'une version déjà publiée remplace ses fichiers et ses notes.

### Analyse VirusTotal de l'APK release

À chaque build sur `main`, la CI envoie l'APK release à [VirusTotal](https://www.virustotal.com) (`tools/virustotal_scan.sh`). Le badge VirusTotal ci-dessus pointe directement vers le rapport public de la dernière analyse : comme son lien dépend du SHA-256 de chaque APK, la CI le met à jour dans ce README à chaque build sur `main`. Le résumé du run donne les mêmes informations : empreinte SHA-256, nombre de détections, lien du rapport. L'analyse est informative : une détection, souvent un faux positif sur une app qui lit le journal d'appels, affiche un avertissement mais ne bloque jamais la release. Le secret `VIRUSTOTAL_API_KEY` active l'analyse ; sans lui, l'étape est ignorée et le badge garde son dernier lien connu.

### Vérifier la signature d'un APK de release
Les APK de release construits sur `main` sont signés avec la clé d'upload stable du projet (secret `KEYSTORE_BASE64`). Pour vérifier qu'un APK téléchargé provient bien de cette clé (par ex. sous Termux, `pkg install apksigner`) :
```sh
apksigner verify --print-certs info-operateur-<version>.apk
```
Résultat attendu :
```text
Signer #1 certificate DN: CN=slashetc.net, O=slashetc, C=FR
Signer #1 certificate SHA-256 digest: 1f45c660f2385aee7bdd9aa05eaa562d8b4caf7fcfcb32c386a2f2fdd8ed793a
```
- Si l'empreinte SHA-256 diffère, l'APK n'a pas été signé avec la clé du projet (secret absent ou modifié) : il ne pourra pas mettre à jour une installation existante.
- `keytool -printcert -jarfile` n'affiche rien pour cet APK : avec `minSdk` 24, seuls les schémas de signature v2+ sont utilisés, que `keytool` ne sait pas lire.

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
│   │   ├── java/net/slashetc/callinspector/
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

## 7. TODO & Pistes d'évolution

### TODO initiale : Comment la base de donnée de l'ARCEP est-elle maintenue à jour ?
> **Statut : ✅ Implémenté et fonctionnel**

La question de la fraîcheur des données de l'ARCEP a été résolue par la mise en place d'une architecture à double niveau (statique & dynamique) :

1. **Au niveau du code source et des releases (CI/CD Automatisée)** :
   - Le script Python `tools/update_arcep_db.py` interroge les serveurs officiels de l'ARCEP (`MAJNUM.csv` et `identifiants_CE.csv`).
   - Le workflow GitHub Actions (`.github/workflows/update_arcep.yml`) s'exécute automatiquement deux fois par mois (les 1er et 15) pour générer une nouvelle version compilée de `arcep_data.db` dans les assets du projet.
   - Les checksums SHA-256 et métadonnées de version sont stockés dans la table `arcep_metadata`.

2. **Au niveau de l'application installée (Mise à jour Over-The-Air / OTA)** :
   - L'utilisateur n'a pas besoin d'attendre une mise à jour d'APK sur le Play Store pour bénéficier des dernières attributions de numéros.
   - Le composant `ArcepUpdateManager.kt` télécharge les flux ARCEP directement dans l'application, reconstruit une base SQLite locale sans blocage de l'interface, puis bascule de manière atomique sur la nouvelle base.
   - Un bouton interactif et une jauge de progression sont disponibles dans l'onglet **Observatoire**.

### Pistes d'évolution futures (Roadmap)
- [ ] **Mise à jour automatique en arrière-plan via Android WorkManager** : planifier une vérification silencieuse mensuelle en Wi-Fi lorsque l'appareil est en charge.
- [ ] **Détection en temps réel des appels entrants (Call Screening Service)** : afficher le titulaire légal ARCEP et l'avertissement de démarchage directement sur l'écran d'appel Android (`TelecomManager` / `CallScreeningService`).
- [ ] **Export / Import des notes et favoris** au format JSON ou CSV pour sauvegarde locale.
- [ ] **Recherche géolocalisée des ZAB (Zones de numérotation élémentaire)** : cartographie interactive des zones géographiques associées aux indicatifs fixes (01 à 05).

---

## Maintenance et mise à jour de la base ARCEP (Détails techniques)

La base officielle de l'ARCEP est maintenue à jour à travers deux mécanismes complémentaires implémentés dans le projet :

### 1. Script d'import et CI/CD automatisée (`tools/update_arcep_db.py`)
Un script Python autonome est disponible dans le dépôt :
```sh
python3 tools/update_arcep_db.py --output app/src/main/assets/arcep_data.db
```
- **Téléchargement direct** des exports officiels de l'ARCEP :
  - [`MAJNUM.csv`](https://extranet.arcep.fr/uploads/MAJNUM.csv) (ressources de numérotation).
  - [`identifiants_CE.csv`](https://extranet.arcep.fr/uploads/identifiants_CE.csv) (identifiants et adresses des opérateurs).
- **Contrôle d'intégrité et empreinte SHA-256** calculée pour chaque fichier source.
- **Préservation des données utilisateur** : les notes, favoris et statuts de spam vivent dans leur propre base chiffrée, que les mises à jour ARCEP ne touchent pas.
- **Indexation B-Tree optimisée** (`idx_tranche`, `idx_ezabpqm`, `idx_op_code`) et compression SQLite (`VACUUM`).
- **Workflow GitHub Actions** (`.github/workflows/update_arcep.yml`) planifié le 1er et le 15 de chaque mois à 04:00 UTC pour mettre à jour la base embarquée du dépôt automatiquement.

### 2. Mise à jour dynamique Over-The-Air (OTA) dans l'application (`ArcepUpdateManager.kt`)
L'utilisateur peut actualiser sa base ARCEP directement depuis son smartphone, sans attendre une mise à jour d'APK :
- **Moteur de mise à jour local** (`ArcepUpdateManager.kt`) : télécharge les flux CSV officiels, compile la base dans un fichier SQLite temporaire, et permute atomiquement la base active (`replaceDatabaseFile`).
- **Interface utilisateur dédiée dans l'Observatoire** (`StatsAndInfoScreen.kt`) :
  - Affiche l'état de la mise à jour (progression en pourcentage, étapes en temps réel).
  - Bouton interactif *« Vérifier et actualiser la base ARCEP »*.

### 3. Traçabilité de la version de la base (`arcep_metadata`)
Une table SQLite dédiée `arcep_metadata` enregistre la provenance exacte :
- `version_date` : date de dernière modification de l'export ARCEP (`Last-Modified`).
- `generated_at` : horodatage précis de la génération de la base.
- `latest_attribution_date` : date d'attribution la plus récente accordée par l'ARCEP.
- `majnum_sha256` et `ce_sha256` : empreintes cryptographiques pour auditabilité.

### 4. Optimisation des dépendances (`app/build.gradle.kts`)
- Les dépendances de scaffold superflues (Firebase AI, AppCheck, Retrofit) ont été désactivées pour réduire l'empreinte mémoire et la taille de l'APK.
- Le client HTTP `OkHttp` est conservé pour le moteur de téléchargement OTA sécurisé de l'ARCEP.

---

## Suivi d'exceptions et crash reporting (Sentry / GlitchTip)

L'application intègre le SDK Sentry pour le monitoring des exceptions en production (également compatible GlitchTip) avec des garanties renforcées de confidentialité et de portabilité.

### 1. Configuration Gradle (`app/build.gradle.kts`)
- **Plugin Sentry** : Version officielle `6.22.0` (`io.sentry.android.gradle`), compatible AGP 9+ et Kotlin 2.2+.
- **Tracing Instrumentation** : Le bloc `tracingInstrumentation` est activé pour instrumenter les requêtes de base de données Room/SQLite et les appels réseau OkHttp.
- **Désactivation de l'upload des mappings** (`autoUploadProguardMapping.set(false)`) : Évite l'exécution locale de `sentry-cli` (qui échoue sous Termux et les architectures sans binaire natif).

### 2. Désactivation en mode Debug (`src/debug/AndroidManifest.xml`)
L'option `ignoredBuildTypes` du plugin ne coupant pas l'envoi d'événements au runtime, Sentry est explicitement désactivé pour tous les builds de debug via la métadonnée manifest :
```xml
<meta-data
    android:name="io.sentry.enabled"
    android:value="false" />
```

### 3. Respect absolu de la vie privée et confidentialité des appels
L'application manipulant le journal d'appels personnel de l'utilisateur, des règles strictes de non-divulgation sont appliquées dans `OperatorInfoApp.kt` via le callback `beforeSend` :
- **Aucune donnée personnelle transmise** : `event.user` est forcé à `null` (aucun identifiant, email ou nom d'utilisateur n'est associé).
- **Nettoyage automatique des breadcrumbs** : Tous les événements de navigation et d'interface ont leurs messages et données assainis. Les clés contenant `phone`, `number`, `call`, `contact`, `email` ou `note` sont purgées.
- **Masquage regex** : Les numéros de téléphone sont automatiquement remplacés par `[REDACTED_PHONE]` et les adresses courriel par `[REDACTED_EMAIL]`.

### 4. Gestion des secrets et du DSN
- Le DSN ou token Sentry n'est **jamais stocké en dur** dans le dépôt git.
- Il est injecté au moment du build via la variable d'environnement `SENTRY_DSN` dans `BuildConfig.SENTRY_DSN` ou lu à l'exécution via `System.getenv("SENTRY_DSN")`.
- En l'absence de DSN (valeur vide par défaut), le SDK reste inactif sans émettre de requête.

---

## Spécificités de build sous Termux (Android ARM64)

Pour compiler le projet directement sous un terminal Android Termux :
1. **AAPT2 natif** : Gradle télécharge par défaut un binaire `aapt2` compilé pour Linux x86_64, incompatible avec l'architecture ARM64 de Termux. Le fichier `settings.gradle.kts` détecte automatiquement si `/data/data/com.termux/files/usr/bin/aapt2` est présent et configure l'override `android.aapt2FromMavenOverride`. Vous pouvez aussi décommenter la ligne dédiée dans `gradle.properties`.
2. **SDK Android & local.properties** : Sous Termux, votre fichier `local.properties` (non versionné) doit définir `sdk.dir` vers votre dossier SDK (ex: `sdk.dir=/data/data/com.termux/files/usr/share/android-sdk`). Vérifiez que `compileSdk` (36) et les build-tools correspondent aux paquets installés via `pkg`.

---

## Licence

Le code est distribué sous licence **MIT** (voir [`LICENSE`](LICENSE)) : vous pouvez l'utiliser, le modifier et le redistribuer librement, y compris dans un projet commercial, à condition de conserver la mention de copyright et le texte de la licence.

Les données de numérotation embarquées (`app/src/main/assets/arcep_data.db`) proviennent des fichiers publics de l'ARCEP (`MAJNUM.csv` et `identifiants_CE.csv`, voir `tools/update_arcep_db.py`). Elles ne sont pas couvertes par la licence MIT et restent soumises aux conditions de réutilisation de l'ARCEP ; mentionnez la source (« ARCEP ») et la date de mise à jour si vous les réutilisez.

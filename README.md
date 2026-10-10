# Info Opérateur

[![Build](https://github.com/azman0101/call-inspector/actions/workflows/build_apk.yml/badge.svg?branch=main&event=push)](https://github.com/azman0101/call-inspector/actions/workflows/build_apk.yml?query=branch%3Amain+event%3Apush)
[![VirusTotal](https://img.shields.io/badge/VirusTotal-rapport%20de%20l%27APK%20release-394EFF?logo=virustotal&logoColor=white)](https://www.virustotal.com/gui/file/ace6ad6ed8488f52c7032e195ee0e2acdffe3074f9eabdb16a3d4b4050a73703)
[![Dernière release](https://img.shields.io/github/v/release/azman0101/call-inspector?label=t%C3%A9l%C3%A9charger%20l%27APK)](https://github.com/azman0101/call-inspector/releases/latest)
[![Licence MIT](https://img.shields.io/badge/licence-MIT-green)](LICENSE)

Application Android (Kotlin, Jetpack Compose) qui identifie, pour chaque appel reçu, l'opérateur et l'entreprise titulaires du numéro d'après les données publiques de l'**ARCEP**, et aide à signaler le démarchage abusif (SignalConso, J'alerte l'Arcep). Anciennement « Arcep Opérateur », elle a été renommée pour respecter la marque de l'ARCEP et les règles du Play Store.

> [!NOTE]
> **Code largement produit par IA.** L'essentiel du dépôt (code, tests, CI, scripts, documentation) a été écrit par des assistants de programmation, sous la direction et la relecture du mainteneur : Google AI Studio au départ (d'où l'ancien identifiant `com.aistudio.operatorlookup`), puis Claude Code, Google Jules et l'agent GitHub Copilot, dont les commits portent le nom ou un trailer `Co-Authored-By`. La plupart des changements passent par une pull request et la CI (tests unitaires et Robolectric, scan OWASP, GitGuardian), et le mainteneur décide des fusions. Le code n'a pas eu d'audit indépendant : relisez-le avant de le réutiliser.
>
> À titre indicatif, une session Claude Code de trois jours (PR #29 à #35) a demandé environ 1 000 réponses du modèle, soit environ 115 $ au tarif public de l'API Anthropic, aux deux tiers des lectures du cache de prompt.

## Installer

Téléchargez `info-operateur-<version>.apk` depuis la [dernière release](https://github.com/azman0101/call-inspector/releases/latest) et ouvrez-le sur le téléphone (Android 7.0 ou plus récent) : il met à jour une installation existante.

- Chaque build de `main` publie une [release](https://github.com/azman0101/call-inspector/releases) `v1.0.<numéro du run>` : l'APK signé, son fichier `.sha256`, et des notes avec les empreintes, le rapport VirusTotal et les pull requests fusionnées.
- L'application vérifie au plus une fois par jour si une version plus récente est publiée et l'annonce avec ses nouveautés (réglage *Vérifier les mises à jour*).
- Le badge VirusTotal mène au rapport de la dernière release. L'analyse est informative : une détection est souvent un faux positif pour une app qui lit le journal d'appels.
- L'APK debug des pull requests est une autre application (« Info Opérateur (debug) », tuile « Qui m'a appelé ? (debug) ») : il s'installe à côté de la release au lieu de la mettre à jour.

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
(À partir des build-tools 37, `apksigner` écrit `V2 Signer: certificate …` au lieu de `Signer #1 certificate …` : seule l'empreinte compte.)
- Si l'empreinte SHA-256 diffère, l'APK n'a pas été signé avec la clé du projet (secret absent ou modifié) : il ne pourra pas mettre à jour une installation existante.
- `keytool -printcert -jarfile` n'affiche rien pour cet APK : avec `minSdk` 24, seuls les schémas de signature v2+ sont utilisés, que `keytool` ne sait pas lire.

## Fonctionnalités

- **Journal d'appels** : les 100 derniers appels reçus (entrants, manqués, rejetés, bloqués ; les appels sortants sont ignorés), avec l'opérateur attributaire de chaque numéro. Filtres *Tous*, *⚠️ Démarchage / Spam*, *Manqués*, *Entrants*, *Favoris*, et recherche par numéro, contact ou opérateur. Sans l'autorisation d'accès au journal, l'app propose 10 appels d'exemple ; après un refus définitif, un bouton ouvre ses réglages.
- **Fiche ARCEP d'un numéro** : opérateur titulaire de la tranche, code exploitant, SIRET, RCS, siège, plage de numérotation, territoire, dates d'attribution et de déclaration. « Consulter le portail officiel (arcep.fr) » copie le numéro et ouvre la page de l'ARCEP pour vérifier.
- **Démarchage repéré** : les numéros des plages réservées au démarchage depuis le 1er janvier 2023 (décision ARCEP n° 2022-1583) sont signalés : `0162`-`0163`, `0270`-`0271`, `0377`-`0378`, `0424`-`0425`, `0568`-`0569`, `0948`-`0949` et `09475` à `09482`. Démarcher depuis un mobile (06, 07) est interdit.
- **Recherche manuelle** : un numéro ou un préfixe (`01 62`, `06 12`…), saisi ou collé, et toutes les tranches qui y correspondent.
- **Export des appels** : « Exporter / copier » copie les appels choisis (date et heure), en Markdown ou en CSV, avec la ligne appelée et, au choix, vos notes, par exemple pour répondre à l'opérateur qui les demande.
- **Tuile « Qui m'a appelé ? »** (Réglages rapides) : le numéro, l'heure et l'opérateur du dernier appel manqué, rejeté ou bloqué, sans ouvrir l'app (après déverrouillage du téléphone). « Voir dans l'app » ouvre le journal sur ce numéro.
- **Signalement sur SignalConso** : depuis un appel reçu, le formulaire officiel « Démarchage abusif » s'ouvre dans l'app, prérempli : numéro, dates, description, motif et entreprise (le nom de l'appelant, sinon l'opérateur attributaire), proposés d'après votre note et l'historique de l'appel. Un bandeau signale les choix faits par défaut. Vous vérifiez chaque étape et envoyez vous-même.
- **Alerte à l'Arcep (J'alerte l'Arcep)** : pour un appel ou pour tous les appels de démarchage venus de numéros d'un même opérateur, utile quand l'appelant est à l'étranger et que SignalConso ne peut rien faire. Chaque étape est préremplie (contexte, type des numéros, opérateur, commune, description, coordonnées) ; le consentement et l'envoi restent à vous.
- **Mes coordonnées** : identité, email, téléphone, numéro de référence, code postal et commune, choix de partage, mémorisés pour préremplir les deux formulaires.
- **Suivi des signalements** : les signalements SignalConso et les alertes Arcep envoyés sont comptés par numéro, avec la date du dernier, rappelés sur la fiche de l'appel.
- **Notes et favoris** : favoris, numéros marqués indésirables, notes personnelles par numéro.
- **Observatoire** : version de la base ARCEP et mise à jour à la demande, analyse de votre journal (démarchage, appels manqués, opérateurs les plus fréquents), règles du démarchage.
- **Copie par appui long** : un appui long sur une information copie sa valeur, sans son libellé et en entier ; un numéro (téléphone, SIRET) est copié sans espaces.
- **Base ARCEP à jour** : l'app la met à jour seule, au premier démarrage puis au plus une fois par semaine, seulement si les fichiers publiés par l'ARCEP ont changé.

## Confidentialité

Le texte de référence est [`legal/CGU.md`](legal/CGU.md) : l'application l'affiche et demande de l'accepter au premier lancement et à chaque modification. En bref :

- Le journal d'appels, les notes, favoris et recherches sont traités sur le téléphone. L'opérateur d'un numéro est cherché dans la base embarquée, pas sur `arcep.fr`, dont la page de recherche bloque d'ailleurs les requêtes automatisées.
- Les notes, vos coordonnées et l'historique des signalements sont stockés dans des bases chiffrées (SQLCipher, clé protégée par l'Android Keystore), exclues des sauvegardes et des transferts d'appareil.
- Rien ne quitte le téléphone sans vous, sauf dans un signalement SignalConso ou une alerte J'alerte l'Arcep que vous validez. Les formulaires ne reçoivent vos coordonnées qu'à l'étape qui les demande.
- Connexions : `extranet.arcep.fr` (mise à jour de la base) et `api.github.com` (nouvelle version, désactivable), sans aucune donnée vous concernant ; `signal.conso.gouv.fr`, `entreprise.signal.conso.gouv.fr` et `jalerte.arcep.fr` quand vous ouvrez un formulaire ; Sentry seulement si vous l'activez.
- Les rapports d'anomalies (Sentry) sont désactivés par défaut : sans votre accord, le SDK ne démarre pas. Activés, ils envoient des données techniques liées à un identifiant d'installation aléatoire ; numéros, contacts, notes, noms et emails sont filtrés avant l'envoi.
- La revue de sécurité de l'application est dans [`SECURITY_REVIEW.md`](SECURITY_REVIEW.md).

## Limites d'interprétation

- **Portabilité des numéros** : l'ARCEP attribue des tranches aux opérateurs, mais un abonné peut avoir porté son numéro chez un autre opérateur, et la base des numéros portés n'est pas publique. L'app donne l'opérateur attributaire de la tranche.
- **Nature du démarchage** : qu'un numéro appartienne à une plage de démarchage (par exemple `0162`) indique un usage réservé à la prospection commerciale, mais ne préjuge pas à lui seul de la légalité ou du caractère frauduleux d'une société.

## Développement

**Prérequis** : JDK 21, Android SDK avec `platforms;android-36.1` et `build-tools;36.0.0`, et `ANDROID_HOME` défini (ou `sdk.dir` dans `local.properties`).

```sh
./gradlew assembleDebug        # APK debug : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests unitaires et Robolectric
./gradlew lintDebug
./gradlew dependencyCheckAnalyze --no-configuration-cache   # scan OWASP des dépendances
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

En cas d'erreurs HTTP 429 de Maven Central, `-PmavenCentralMirror=true` passe par le miroir de Google (voir [`AGENTS.md`](AGENTS.md), qui donne aussi les règles de suppression des CVE).

- **CI** (`.github/workflows/build_apk.yml`) : chaque pull request vers `main` lance les tests et le scan OWASP (indicatif), et produit l'APK debug en artefact `info-operateur-debug-v<version>` (14 jours). Chaque push sur `main` relance les tests et le scan (bloquant), construit l'APK release signé, l'envoie à VirusTotal et publie la release (`tools/publish_release.sh`).
- **Base ARCEP** : `tools/update_arcep_db.py` construit `app/src/main/assets/arcep_data.db` à partir de `MAJNUM.csv` et `identifiants_CE.csv`. Il se lance à la main et son résultat passe par une pull request relue : la CI ne publie plus de base (SR-02). Sur le téléphone, `ArcepAutoUpdate` et `ArcepUpdateManager` font la mise à jour (dates `Last-Modified`, contrôle de vraisemblance, bascule atomique) ; la table `arcep_metadata` garde les dates et les SHA-256 des fichiers sources. Un numéro est rapproché de la tranche qui le contient, sinon du plus long préfixe connu (`ArcepDatabaseManager.lookupNumbers`).
- **Sentry** : le DSN est injecté au build par la variable `SENTRY_DSN`, avec un DSN par défaut dans `SentryHelper`. Le filtrage des événements est dans `OperatorInfoApp.kt`. L'envoi des mappings ProGuard est désactivé (`sentry-cli` ne fonctionne pas sous Termux).
- **Termux (Android ARM64)** : `bash setup.sh`, puis `./gradlew assembleDebug`. Le script installe le JDK, le SDK et un `aapt2` natif AArch64 (celui de Termux est trop ancien pour le SDK 35 et plus), qu'il déclare dans `~/.gradle/gradle.properties` ; les détails sont dans son en-tête.

## Structure du projet

| Dossier | Contenu |
| --- | --- |
| `app/src/main/java/net/slashetc/callinspector/data/db` | base ARCEP (`ArcepDatabaseManager`) et bases chiffrées (notes, coordonnées, signalements) |
| `…/data/repository` | journal d'appels, mises à jour de la base ARCEP et de l'application |
| `…/ui` | écrans Compose, formulaires SignalConso et J'alerte l'Arcep (WebView), tuile |
| `…/util` | règles métier : signalement, alerte, export, mises à jour, CGU, formatage des numéros |
| `…/viewmodel` | état de l'interface |
| `app/src/main/assets` | `arcep_data.db`, scripts de préremplissage des formulaires, liste des opérateurs de J'alerte l'Arcep |
| `app/src/test` | tests unitaires et Robolectric |
| `legal` | CGU affichées par l'application |
| `tools` | base ARCEP, releases, VirusTotal, configuration de la CI |
| `config` | suppressions documentées du scan OWASP |

## Feuille de route

- [ ] **Identification pendant l'appel** (`CallScreeningService`) : afficher le titulaire ARCEP et l'avertissement de démarchage sur l'écran d'appel.
- [ ] **Sauvegarde et restauration des notes et favoris** (JSON ou CSV).
- [ ] **Carte des zones de numérotation (ZNE)** des numéros fixes (01 à 05).

## Licence

Le code est distribué sous licence **MIT** (voir [`LICENSE`](LICENSE)) : vous pouvez l'utiliser, le modifier et le redistribuer librement, y compris dans un projet commercial, à condition de conserver la mention de copyright et le texte de la licence.

Les données de numérotation embarquées (`app/src/main/assets/arcep_data.db`) proviennent des fichiers publics de l'ARCEP (`MAJNUM.csv` et `identifiants_CE.csv`, voir `tools/update_arcep_db.py`). Elles ne sont pas couvertes par la licence MIT et restent soumises aux conditions de réutilisation de l'ARCEP ; mentionnez la source (« ARCEP ») et la date de mise à jour si vous les réutilisez.

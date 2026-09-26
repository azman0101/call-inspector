# Conditions Générales d'Utilisation (CGU) & Politique de Confidentialité

*Dernière mise à jour : 26 septembre 2026*

L'application **Info Opérateur** (« l'Application ») est un outil utilitaire open source et indépendant dédié à la consultation et à l'identification des tranches de numérotation téléphonique en France d'après les données publiques de l'ARCEP.

En installant ou en utilisant l'Application, vous acceptez sans réserve les présentes Conditions Générales d'Utilisation.

---

## 1. Absence totale de garantie (« As is »)

L'Application est mise à votre disposition gratuitement, « en l'état » (*AS IS*) et selon sa disponibilité, sans aucune garantie d'aucune sorte, expresse ou tacite.

1. **Exactitude des informations** : Les correspondances de numéros et noms d'opérateurs proviennent des publications ouvertes et décisions d'attribution de l'ARCEP (plan national de numérotation). Ces informations indiquent l'opérateur attributaire de la tranche initiale. Elles ne sauraient garantir l'identité de l'abonné final ou de l'opérateur exploitant effectif en cas de portabilité de la ligne.
2. **Fonctionnement ininterrompu** : Aucune garantie n'est donnée quant à l'absence de bugs, d'erreurs ou d'interruptions du service.
3. **Limitation de responsabilité** : Le développeur ou contributeur de l'Application ne saurait en aucun cas être tenu responsable de tout dommage direct ou indirect (perte de données, appels non reconnus, préjudice commercial, incident de sécurité) résultant de l'utilisation ou de l'impossibilité d'utiliser l'Application.

---

## 2. Indépendance et absence d'affiliation

Info Opérateur est une initiative tierce indépendante. **L'Application n'est ni éditée, ni affiliée, ni parrainée, ni approuvée par l'Autorité de régulation des communications électroniques, des postes et de la distribution de la presse (ARCEP)**, ni par aucun opérateur de télécommunications.

---

## 3. Absence de collecte de données d'identification (PII)

La protection de la vie privée et le principe de minimisation (RGPD) sont au cœur de l'architecture de l'Application (*Privacy by Design*) :

1. **Traitement 100 % local sur votre terminal** :
   - Les numéros de téléphone saisis ou lus dans votre journal d'appels sont analysés **exclusivement en local** sur votre appareil grâce à une base SQLite embarquée.
   - Vos contacts, journaux d'appels, notes personnelles et requêtes de recherche ne quittent **jamais** votre appareil.
2. **Aucune création de compte ni profilage** : L'Application ne requiert aucun compte, aucune adresse email, aucun nom d'utilisateur ni identifiant publicitaire.
3. **Nettoyage strict côté client** : Tout identifiant persistant de l'appareil (`device.id`, horodatage de démarrage) et tout contenu susceptible de ressembler à un numéro de téléphone ou une adresse email sont automatiquement purgés en amont par des filtres locaux stricts.

---

## 4. Absence de serveur tiers appartenant au développeur

**L'Application n'établit aucune connexion avec un quelconque serveur propriétaire ou infrastructure gérée par le développeur.**

Les seuls échanges réseau que l'Application est susceptible d'effectuer se limitent strictement à :
1. **La mise à jour de la base de numérotation (OTA)** : Téléchargement direct des fichiers publics officiels (`MAJNUM.csv`, `identifiants_CE.csv`) depuis l'extranet officiel de l'ARCEP (`extranet.arcep.fr`). La connexion est chiffrée en HTTPS et protégée par verrouillage de certificat (*TLS certificate pinning*).
2. **L'ouverture de liens web externes (à votre initiative)** : Lorsque vous choisissez d'ouvrir la fiche officielle sur `arcep.fr` ou le formulaire de signalement `jalerte.arcep.fr` dans votre navigateur.
3. **Le service tiers de diagnostic technique d'anomalies (Sentry / GlitchTip)**, soumis au consentement explicite préalable (voir article 5).

---

## 5. Rapports techniques d'anomalies strictement « Opt-In »

Conformément à la réglementation européenne (RGPD), **la collecte de diagnostics techniques d'anomalies est désactivée par défaut (Opt-In strict)**.

1. **Par défaut** : Aucun rapport de crash, aucune métrique d'utilisation, aucun log ni aucune trace réseau ne sont transmis. Le SDK de télémétrie n'est pas initialisé.
2. **Activation volontaire (Opt-In)** : Vous pouvez choisir d'activer manuellement les *« Rapports techniques d'anomalies »* dans l'écran *Observatoire & Cadre Légal* > section *Confidentialité & Données*.
3. **Désactivation à tout moment** : Vous pouvez révoquer votre consentement à tout moment d'un simple clic sur le même interrupteur. L'arrêt du collecteur est immédiat.
4. **Contenu des rapports autorisés** : Même lorsque cette option est expressément activée, les rapports ne contiennent que des informations techniques indispensables à la résolution des bugs (type d'exception, version d'Android, modèle générique), épurées au préalable de toute donnée personnelle (PII).

---

## 6. Permissions requises

- **`READ_CALL_LOG`** : Utilisée uniquement pour lire l'historique d'appels local afin d'afficher le nom de l'opérateur en regard de vos correspondants. Cette permission est facultative (un mode démo avec données d'exemples est proposé en cas de refus).
- **`INTERNET`** : Utilisée uniquement pour le téléchargement direct des fichiers ouverts de l'ARCEP lors des mises à jour manuelles de la base, et le cas échéant pour l'envoi de rapports d'erreurs si vous avez activé l'option opt-in.

---

## 7. Droit applicable

Les présentes conditions sont soumises au droit français. Pour toute question ou pour consulter le code source de l'application, vous pouvez vous référer au dépôt public du projet.

# Conditions générales d'utilisation et confidentialité

*Dernière mise à jour : 8 octobre 2026*

**Info Opérateur** identifie l'opérateur d'un numéro de téléphone à partir des données publiques de l'ARCEP et aide à signaler le démarchage abusif sur SignalConso. L'application vous demande d'accepter ces conditions à sa première ouverture, puis à chaque modification.

---

## 1. Service fourni en l'état

1. L'application est gratuite et fournie « en l'état », sans garantie de fonctionnement ni d'exactitude.
2. Les données de l'ARCEP indiquent l'opérateur auquel une tranche de numéros a été attribuée. Elles n'identifient pas l'appelant, et l'opérateur réel peut différer si le numéro a été porté.
3. Le développeur n'est pas responsable des dommages résultant de l'utilisation de l'application.

## 2. Indépendance

L'application n'est ni éditée, ni approuvée par l'ARCEP, la DGCCRF (SignalConso) ou un opérateur de télécommunications.

## 3. Vos données restent sur votre téléphone

1. Votre journal d'appels, vos notes, favoris et recherches sont traités sur votre téléphone. L'application ne les envoie nulle part, sauf dans un signalement ou une alerte que vous validez (article 5).
2. Vos notes, favoris et marquages spam, ainsi que vos coordonnées pour SignalConso et J'alerte l'Arcep (dont votre code postal et votre commune) et l'historique de vos signalements et alertes si vous les enregistrez, sont stockés chiffrés sur le téléphone et exclus des sauvegardes.
3. Aucun compte, aucun identifiant publicitaire, aucun profilage.

## 4. Connexions réseau

L'application n'a pas de serveur. Elle se connecte uniquement à :

1. `extranet.arcep.fr`, pour mettre à jour la base de numérotation : au premier démarrage, puis au plus une fois par semaine, et à votre demande (HTTPS avec vérification du certificat). L'application demande d'abord la date des fichiers publics de l'ARCEP et ne les télécharge que s'ils ont changé. Aucune donnée vous concernant n'est envoyée ;
2. `signal.conso.gouv.fr`, quand vous ouvrez le formulaire de signalement, et `entreprise.signal.conso.gouv.fr`, pour y rechercher l'opérateur par son SIRET ou son nom ;
3. `jalerte.arcep.fr`, quand vous ouvrez le formulaire d'alerte de l'Arcep, y compris pour y chercher votre commune à partir de votre code postal ;
4. Sentry (`sentry.io`, hébergé dans l'Union européenne), seulement si vous activez les rapports d'anomalies (article 6) ;
5. `api.github.com`, pour savoir si une nouvelle version de l'application est publiée et afficher ses nouveautés : au plus une fois par jour, et à votre demande. Seule la liste publique des versions est consultée ; aucune donnée vous concernant n'est envoyée. Vous pouvez le désactiver dans *Observatoire* > *Confidentialité & Données* > *Vérifier les mises à jour*.

Les liens vers `arcep.fr`, ainsi que le téléchargement d'une nouvelle version (`github.com`), s'ouvrent dans votre navigateur.

## 5. Signalement sur SignalConso et alerte à l'Arcep

1. L'application pré-remplit le formulaire officiel. Rien n'est envoyé tant que vous ne validez pas la dernière étape.
2. Le signalement est transmis au service public SignalConso (DGCCRF) : numéro appelant, date(s), description (avec votre note sur l'appel), entreprise signalée et vos coordonnées. Leur traitement relève de la politique de confidentialité de SignalConso, et le site peut utiliser ses propres outils de mesure.
3. Vérifiez chaque pré-remplissage : faute d'identifier l'appelant, l'entreprise proposée est l'opérateur du numéro, et le motif peut être un motif par défaut. Vous êtes responsable de l'exactitude de votre signalement.
4. *Alerter l'Arcep* pré-remplit de la même façon J'alerte l'Arcep, la plateforme d'alerte de l'Arcep, pour un appel ou pour tous les appels de démarchage reçus de numéros d'un même opérateur. Sont pré-remplis : le type de numéro, l'opérateur auquel l'Arcep a attribué le ou les numéros, une description (numéros, dates et heures des appels, votre note), votre commune et vos coordonnées si vous les avez enregistrées. Rien n'est envoyé tant que vous ne cliquez pas sur « Envoyer mon alerte », et les consentements demandés par l'Arcep restent à votre choix.
5. L'alerte est transmise à l'Arcep ; son traitement relève des conditions d'utilisation et de la politique de données personnelles de J'alerte l'Arcep.

## 6. Rapports d'anomalies (désactivés par défaut)

1. Par défaut, aucune donnée de diagnostic n'est envoyée.
2. Si vous activez les *Rapports techniques d'anomalies*, l'application envoie des données techniques (erreurs, performances, stabilité) associées à un identifiant d'installation aléatoire, qui ne permet pas de vous identifier. Numéros, contacts, notes, noms et adresses email sont filtrés avant tout envoi.
3. Vous pouvez les désactiver à tout moment dans *Observatoire* > *Confidentialité & Données* : l'envoi s'arrête immédiatement.

## 7. Permissions

- `READ_CALL_LOG` : lire le journal d'appels pour afficher l'opérateur de chaque numéro. Facultative : sans elle, l'application fonctionne avec des exemples.
- `INTERNET` et `ACCESS_NETWORK_STATE` : les connexions de l'article 4.

## 8. Droit applicable

Ces conditions sont soumises au droit français. Le code source de l'application est consultable sur le dépôt public du projet.

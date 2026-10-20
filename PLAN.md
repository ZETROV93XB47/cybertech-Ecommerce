# Plan

- [x] **Prompt front 12 — carte : suppression sans effet, 2e ajout en 500, remplacement** (2026-10-20) :
  - [x] `deleteBankCard` (et suppression admin) : dissocier la carte de l'utilisateur pour que la suppression ne soit plus annulée par la cascade.
  - [x] `BankCardAlreadyExistsException` → 409 `BANK_CARD_ALREADY_EXISTS` (ErrorCode, handler, OpenAPI).
  - [x] `PUT /bank-card/replace` : validation puis suppression + ajout dans une seule transaction.
  - [x] Tests unitaires + IT (add → delete → get 404 → add 201 ; add en double 409 ; replace 200 ; replace invalide 400 et ancienne carte conservée) ; `mvnw test` vert (2 344 tests, 0 échec) ; `BankCardFlowIT` 5/5 ; OpenAPI, `progress.md`, commit.
- [ ] **Prompt front 13 — `GET /review/mine`** (2026-10-20) :
  - [ ] Repository + service + `MyReviewDto` + endpoint paginé (size 10, tri `createdAt,desc`), avis de l'appelant uniquement.
  - [ ] OpenAPI, tests (unitaires + IT : seulement mes avis, pagination, tri, 401) ; `mvnw test` vert ; `progress.md`, commit.

- [x] **Prompt front 11 — détail structuré de `NOT_ENOUGH_STOCK`** (2026-10-20, estimé < 2 h, sans sous-agent) :
  - [x] `exceptions` — `StockShortage` (productUuid, requested, available ≥ 0) + `NotEnoughStockException` porteuse d'une liste.
  - [x] `StockServiceImp` — `reserveStock` / `resizeReservation` vérifient toutes les lignes puis lèvent une seule exception listant tous les défauts ; `CartStockValidator` fournit UUID/demandé/disponible.
  - [x] `api/error/model` — `StockShortageDto`, `ErrorDetailsDto`, champ `details` (NON_NULL) dans `ErrorResponseDto` ; `ErrorManagementController` le remplit (409 et errorCode inchangés).
  - [x] OpenAPI — 409 `NOT_ENOUGH_STOCK` avec exemple sur `POST /cart/add`, `PATCH /cart/update/{uuid}`, `POST /order/place`, `/update`, `/retry-payment/{uuid}` ; `docs/openapi/openapi.yaml` régénéré.
  - [x] Tests unitaires (lanceurs, available à 0, handler, MVC) + ITs (panier, place, update, 404 sans details) ; `mvnw test` vert (2 329 tests, 0 échec, 17 ignorés) ; `CartFlowIT` + `OrderFlowIT` : 24/24.
  - [x] `progress.md`, commit.

- [x] **Corriger les identifiants MySQL du backend Minikube** :
  - [x] Configurer l'application avec le compte `rookie` et le même `mysql-secret/user-password` que le conteneur MySQL.
  - [x] Retirer le passage du mot de passe root vers le secret de l'application.
  - [x] Vérifier le rendu Helm et appliquer le correctif ; les nouveaux logs ne montrent plus le refus MySQL.
  - [x] Récupérer le statut Helm après l'interruption de l'attente de readiness, sans supprimer ni modifier les données persistantes.
  - [x] Committer uniquement les changements de cette correction.
- [x] **Débloquer le backend Minikube — Elasticsearch** :
  - [x] Diagnostiquer le démarrage : la claim Elasticsearch de 5 Gi contient un index créé par Elasticsearch 10, illisible par le pod 8.15.3.
  - [x] Confirmer avec le propriétaire la réinitialisation destructive du volume Elasticsearch pour repartir proprement.
  - [x] Aligner la version déclarée du chart avec son image 9.2.5.
  - [x] Claim Elasticsearch déjà recréée lors de la session précédente : le pod 9.2.5 démarre sur un volume vide.
  - [x] MongoDB : données en FCV 6.0 illisibles par 8.2.5 → montée 7.0 → 8.0 → 8.2.5 sans perte (aucune base applicative présente).
  - [x] Keycloak 26 : retirer la politique JS « Default Policy » (et sa permission) du realm, dans les deux copies.
  - [x] Gorse 0.5.11 : Redis passé de 7.2 à 8.10.2 (commandes `FT.*` requises).
  - [x] Appliquer le Helmfile avec les secrets du dev (`minikube-dev-secrets.sh`) : 11 releases `deployed`, backend `UP` (db, mongo, redis, elasticsearch).
  - [x] Corrections révélées au déploiement : Redis lancé en `args` (modules), chart Stripe sans port/sondes, checksum des secrets, `spring.mongodb.uri` sans espaces finaux.
- [ ] **Charts et Skaffold** (2026-10-20) :
  - [x] Épingler les images (`latest` / sans tag) et aligner Compose sur Helm (Keycloak 26.0.4, Redis 8.10.2, Gorse 0.5.11, RustFS 1.0.3, Mailpit v1.31.6, aws-cli 2.38.2).
  - [x] Retirer l'environnement Helmfile `production` sans fichier de valeurs.
  - [x] Configurer Jib (`eclipse-temurin:26-jre`, `--enable-preview`, classe principale) et réécrire `skaffold.yml` pour la seule release `cybertech-app`.
  - [x] `skaffold run` : build Jib OK (~11 s, image dans le Docker de Minikube) ; deploy bloqué sous Windows sans le mode développeur (plugin Helm 4 installé en symlink).
  - [ ] Activer le mode développeur Windows (propriétaire) puis revalider `skaffold run`.
  - [x] Tests Maven, `progress.md`, commit.
- [x] **Supprimer Vault du projet et remplacer son usage par des secrets Kubernetes/env** :
  - [x] Migrer les placeholders secrets de `application-prod.properties` vers des variables d'environnement et retirer la dépendance/configuration Spring Vault.
  - [x] Retirer Vault de Docker Compose et Helm (chart, options du chart applicatif et chart Stripe).
  - [x] Actualiser les consignes locales/Minikube et les commentaires de configuration liés aux secrets.
  - [x] Vérifier le module `com.novatech.cybertech` (`mvn test`: 2 287 tests, 0 échec, 15 ignorés), les 11 charts Helm restants et le rendu Helmfile (45 sources sans Vault).
  - [x] Ne supprimer aucun PVC ; l'ancienne release Vault Minikube a été désinstallée après confirmation du contexte et aucune claim Vault n'existait.
  - [x] Committer uniquement les fichiers de cette tâche, sans embarquer les changements préexistants.
- [x] **Retirer Loki, Grafana et LocalStack de Helm Minikube** (estimation : 15–25 min) :
  - [x] Supprimer les releases Helmfile et leurs charts ; garder Vault désactivé tel quel.
  - [x] Nettoyer les deux runbooks et `progress.md`, inclure les commandes de retrait des anciennes releases et le sort des PVC.
  - [x] Vérifier lint/rendu backend-only et vérifier qu'aucune référence de déploiement active ne reste : 12 charts lintés, 45 sources rendues, sans release/chart retiré.
  - [x] Committer uniquement les fichiers de cette tâche, sans embarquer les changements préexistants.
- [ ] **Retirer le frontend du déploiement Minikube** (estimation : 20–30 min, dans la session de 2 h) :
  - [x] Supprimer `front-app-chart` et sa release/configuration du Helmfile ; garder le backend et son alias OIDC utilisable.
  - [x] Mettre à jour `DEPLOY_MINIKUBE.md`, le runbook Helm et `progress.md` pour le déploiement backend-only.
  - [x] Vérifier `helm lint` sur les 15 charts restants et le rendu des 56 manifests Helmfile : aucun chart/release frontend ni Vault ; host aliases backend S3/OIDC conservés.
  - [x] Committer uniquement les fichiers de cette tâche, sans inclure le fichier frontend déjà staged.
- [ ] **Audit du déploiement Helm Minikube** :
  - [x] Comparer les charts/Helmfile aux configurations applicatives actuelles (RustFS, Gorse, Web Push, HTTP local, images et ports).
  - [x] Valider `helm lint` sur les charts `cybertech-app`, `elasticsearch`, `front-app`, `grafana`, `gorse`, `keycloak`, `loki`, `localstack` (legacy), `mailpit`, `mongodb`, `mysql`, `moderation-api`, `redis`, `rustfs`, `stripe` et `vault` (inactive), puis rendre le Helmfile avec des valeurs d'exemple.
  - [x] Ajouter les charts RustFS/Gorse ; corriger les versions Elasticsearch/MongoDB, les secrets Spring, l'issuer/callback OIDC, CORS, les hôtes HTTP et les URLs de santé/Swagger.
  - [x] Mettre à jour `DEPLOY_MINIKUBE.md` et `src/main/resources/k8s/helm/README.md`.
  - [ ] Appliquer les releases backend-only et tester authentification/OIDC, upload et lecture d'image dans Minikube (cluster arrêté).
  - [x] Vérifier le diff de cette tâche puis committer uniquement ses fichiers, en préservant les changements préexistants.
- [x] **Étendre les attributs des cinq catégories** :
  - [x] Mettre à jour les schémas par défaut de `ProductCategorySchemaInitializer` en gardant tous les nouveaux champs facultatifs.
  - [x] Ajouter et exécuter une migration SQL ciblée des cinq schémas existants dans `mysqlEcommerce`.
  - [x] Adapter les cinq mappeurs de `dataLoader.py` aux champs LDLC, unités normalisées et booléens.
  - [x] Tester les schémas Java et les conversions Python, puis contrôler les produits scrapés existants.
- [x] **Reprise manuelle du scraper par catégorie et position** : ajouter `--resume-category` + `--resume-after`, conserver un index stable dans la progression, tester les URLs déjà enregistrées et documenter l'usage.
- [x] **Scraper LDLC — cible de cette session (< 2 h estimées, environ 30 min)** :
  - [x] `src/main/java/com/novatech/cybertech/utils/scrapper/laptopScrapper.py` — récupérer un JSON temporaire valide après échec Windows de `os.replace`, réessayer les verrous transitoires et signaler clairement un verrou persistant.
  - [x] `src/main/java/com/novatech/cybertech/utils/scrapper/test_laptopScrapper.py` — tester sauvegarde atomique, retry, conservation/récupération des données temporaires (4 tests réussis).
  - [x] Package `com.novatech.cybertech.utils.scrapper` (script Python embarqué; pas de classe Java ou module Maven à tester) — tests Python ciblés et `py_compile` réussis.
  - [x] `CLAUDE.md` et `progress.md` — normes demandées et checkpoint de progression mis à jour.
- [x] Vérifier le workflow GitHub Actions et les exigences du POM Maven racine.
- [x] Aligner le workflow sur le wrapper Maven et Java 26.
- [x] Valider le wrapper et le build Maven du module racine `com.novatech.cybertech` (tests Surefire; les tests Failsafe ne sont pas activés sans le profil d'intégration).
- [x] Mettre à jour `progress.md` avec le résultat et les prochaines étapes.

## Web Push

- [x] Implémenter l'entité et le dépôt d'abonnements, le service d'inscription/révocation et l'API authentifiée (JPA, repository, service, DTO, controller/spec).
- [x] Implémenter la configuration VAPID, la validation des endpoints Web Push, le processeur push et son intégration au système de notification.
- [x] Déclencher le push de confirmation de paiement et d'expédition uniquement après commit.
- [x] Ajouter les tests unitaires de services/processeur/listeners et tests MVC d'abonnement; exécuter les tests ciblés puis `.\mvnw.cmd -B clean verify` (BUILD SUCCESS, 2243 tests, 0 échec, 0 erreur, 15 ignorés).
- [x] Documenter la configuration front/VAPID requise et mettre à jour `progress.md`.
- [ ] Intégrer le Service Worker et l'interface d'abonnement dans le dépôt frontend, configurer les clés VAPID dans Vault et valider les notifications sur un navigateur réel.
- [ ] Vérifier le diff et l'état Git puis committer uniquement les changements de cette tâche, sans embarquer les fichiers déjà staged/non suivis.

## Web Push — correctifs de revue (2026-10-14)

- [x] 1. Enregistrer le fournisseur BouncyCastle (sinon chaque envoi échoue en `NoSuchProviderException`).
- [x] 2. Un envoi par appareil (contexte de notification ciblé sur un abonnement) : un échec ne renvoie plus le push aux autres appareils.
- [x] 3. Le push n'est plus un canal favori : toujours envoyé en plus de l'e-mail/SMS ; `PUSH_NOTIFICATION` refusé à l'inscription, repli sur EMAIL pour les comptes existants.
- [x] 4. Envoi asynchrone avec délai maximal configurable (client HTTP async partagé + timeout ; `PushService#sendAsync` est déprécié et sans timeout).
- [x] 5. 5 appareils maximum par utilisateur (le plus ancien est remplacé).
- [x] 6. `PushService` construit une seule fois ; chemin de la page commande en propriété.
- [x] 7. CVE : jose4j 0.7.9 → 0.9.7, bcprov-jdk18on 1.86, exclusion de async-http-client / jcommander (inutilisés).
- [x] 8. Suppression : vérifier explicitement que l'abonnement appartient à l'appelant (404 / 403).
- [x] 9a. Tests ciblés (88 verts, dont un test réel du gateway sans mock), OpenAPI régénéré, dossier Postman « Push notifications », `docs/web-push.md`.
- [x] 9b. `mvnw test` complet (2281 tests, 0 échec), progress.md, commit.

## Prompt 10 — carte enregistrée chez Stripe (2026-10-14)

Estimation : tout le prompt tient dans une session (le paiement passe par un seul point,
`PaymentServiceImp.processPayment`). Ordre : code → tests unitaires → ITs → docs → commit.

- [x] 1. `StripeCardGateway` (interface + impl SDK) : Customer, SetupIntent, PaymentMethod (retrieve, attach, update, detach), défaut du Customer.
- [x] 2. `UserEntity.stripeCustomerId` (Customer créé à la première demande, clé d'idempotence par utilisateur).
- [x] 3. `POST /bank-card/setup-intent` → `{ clientSecret, publishableKey }` (`stripe.publishable-key`).
- [x] 4. `POST /bank-card/add` `{ setupIntentId }` : SetupIntent du Customer de l'appelant (sinon 403) et `succeeded` (sinon 400) ; carte remplacée en place, ancienne détachée.
- [x] 5. `BankCardEntity` : `stripePaymentMethodId` ajouté, `encryptedNumber` retiré ; script SQL (purge des cartes sans PaymentMethod + drop colonne).
- [x] 6. `PUT /bank-card/update` et `DELETE` (utilisateur et admin) répercutés chez Stripe.
- [x] 7. Paiement : PaymentIntent avec `customer` + `payment_method` de la carte, `off_session`, `confirm` ; `stripe.payment-method` supprimé ; `decline_code` loggé.
- [x] 8. Sans carte utilisable : 404 `BANK_CARD_NOT_FOUND` à place / retry-payment (OpenAPI), pas au devis.
- [x] 9. Inscription : `bankCardCreationRequestDto` retiré de `UserCreateRequestDto`.
- [x] 10. Webhook : métadonnée `order_uuid` toujours posée (test).
- [x] 11. Tests unitaires (processeur, service carte, gateway, controller), ITs (flux carte, commande 4242 → PAID, refus → retry → PAID), `mvnw test`.
- [x] 12. OpenAPI, Postman, progress.md, commit (2328 tests unitaires + 82 ITs verts, gate JaCoCo OK).

## Décisions de revue (2026-10-14, suite)

- [x] 5. Web Push : 4xx définitifs (400, 401, 403, 413...) → notification FAILED sans retry ni rejeu batch (404/410 = abonnement supprimé, 408/429/5xx = réessayable).
- [x] 7. Handler e-mail ORDER_UPDATE (gabarit + tests).
- [x] 12. S3 local : LocalStack → RustFS (MinIO plus distribué) (volume persistant, console web), config, docs.
- [x] 2. Base de dev : purge des cartes héritées + cartes de test Stripe pour les utilisateurs (script rejouable) + doc des cartes de test.
- [x] 14. Supprimer la branche `claude/session-2026-09-25-fixes`.
- [x] Bug Stripe `payment_method_types` corrigé, paiements réels vérifiés.
- [x] Tests (2353 verts), progress.md, commit.

## Retour à la carte saisie + règle 1111 / 0000 (2026-10-14)

Décision du propriétaire : abandon de la carte enregistrée chez Stripe (prompt 10). La carte est de
nouveau saisie, chiffrée (AES/GCM) et stockée par le backend. Stripe et le webhook restent.

- [x] 1. Revert de `99abb53` (setup-intent, Customer, PaymentMethod, gateway, migration) en gardant les apports ultérieurs (push, ORDER_UPDATE, RustFS).
- [x] 2. Règle de paiement : numéro uniquement en `1` → PaymentIntent `pm_card_visa` (réussi) ; tout autre numéro (dont `0000…`) → carte de test Stripe refusée. Webhook inchangé.
- [x] 3. Garder : carte à l'inscription, `retry-payment` sans carte → 404 avant réservation du stock.
- [x] 4. Base de dev : `encryptedNumber` rétabli, colonnes Stripe retirées, cartes `1111…` chiffrées pour les 7 comptes.
- [x] 5. Docs (cartes de test, prompt 10 abandonné), OpenAPI, Postman, tests, progress.md, commit.

## OrderValidationDto — extraction des données bancaires (2026-10-16)

Estimation : périmètre estimé à moins de 2 h, probablement 20–30 min. Aucun sous-agent requis.

- [x] `com.novatech.cybertech.dto.data` — créer `BankCardValidationDto` avec les seules données consommées par la validation (`expiryDate`).
- [x] `OrderValidationDto`, `BankCardValidityValidator`, `OrderManagementServiceImp` — remplacer l'entité bancaire par le sous-DTO et projeter l'entité au point de construction.
- [x] Tests : `BankCardValidityValidatorTest`, `OrderCreationTransactionalDelegateImpTest`, `ActiveUserValidatorTest`, `ChainableOrderValidatorTest` — adapter les constructions et vérifier la projection de l'expiration.
- [x] Lancer les tests ciblés Maven (31 verts) puis `.\mvnw.cmd -B test` (2 321 tests, 0 échec, 15 ignorés).
- [x] Actualiser `progress.md`, vérifier le diff et committer uniquement les fichiers de cette tâche.

## Revalidation des attributs lors du changement de catégorie (2026-10-17)

- [x] Examiner le chemin `ProductManagementServiceImp.update` et ses tests de modification de produit.
- [x] Revalider les attributs existants contre la nouvelle catégorie quand la requête change la catégorie sans fournir `attributes`.
- [x] Ajouter des tests pour catégorie seule (valide/invalide) et préserver le comportement de mise à jour des attributs.
- [x] Exécuter `ProductManagementServiceImpTest` : 22 tests verts.
- [x] Exécuter la suite complète Maven : 2 323 tests, 0 échec, 15 ignorés.
- [x] Actualiser `progress.md`, vérifier le diff et committer uniquement les fichiers de cette correction.

## Remplacement de LocalStack par RustFS — environnement Docker local (2026-10-19)

Périmètre proposé : le lancement local Docker uniquement. Le déploiement Kubernetes/Helm et
`application-prod.properties` restent sur leur configuration actuelle tant qu'un changement de
ce périmètre n'est pas demandé. Estimation initiale : 45 à 90 minutes, dans une session de 2 h.

- [x] `src/main/resources/docker/docker-compose.yml` — retirer le service `localstack` et son volume ; conserver `rustfs` et son volume persistant ; ajouter/configurer une initialisation idempotente du bucket `cybertech-products` (policy de lecture et CORS nécessaires au front), avec attente de disponibilité de RustFS.
- [x] `src/main/resources/application.properties` — faire pointer le profil local sur `http://localhost:9000`, garder le mode path-style, et utiliser les variables d'environnement RustFS sans ajouter de secret au dépôt.
- [x] `LOCAL_LAUNCH_CHECKLIST.md` et `readme.md` — remplacer les instructions/URLs de démarrage local LocalStack par RustFS (API `localhost:9000`, console `localhost:9001`) ; conserver les références Kubernetes LocalStack.
- [x] Vérifier la transition des images existantes — le bucket LocalStack courant est vide ; les URLs enregistrées en `localhost:4566` ne sont pas migrables depuis ce bucket et les images des produits doivent être rechargées. Le front doit employer l'hôte `localhost:9000`.
- [x] Tests Maven ciblés (34 tests, 0 échec) et smoke RustFS : initialisation du bucket, policy publique, upload, GET anonyme avec CORS, suppression ; vérifier le volume Docker nommé `docker_rustfs_data` monté sur `/data`. Le conteneur n'a pas été redémarré pour ne pas interrompre RustFS déjà utilisé.
- [x] Vérifier les références restantes à LocalStack : elles concernent uniquement Helm/Kubernetes, la configuration prod et l'historique.
- [x] Mettre à jour `progress.md` ; valider `docker compose config --quiet` et `git diff --check`.

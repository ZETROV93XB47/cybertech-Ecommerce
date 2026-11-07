# Plan à exécuter : alerte de retour en stock

> Pour lancer : « Exécute `docs/plans/back-in-stock-alert.md` ». Plan écrit le 2026-11-07,
> pas encore commencé. Estimation : une session de 2 h pour le backend + tests unitaires, une
> seconde pour l'IT et la vérification Mailpit / navigateur (même découpage que l'email de
> suggestions).

## Constat (2026-11-07)

- **Rien n'existe** : pas d'entité d'abonnement, pas de type de notification, pas de template.
- **Le stock remonte par beaucoup de chemins**, tous dans `StockServiceImp` ou l'admin :
  - `ProductManagementServiceImp#update` (l'admin change `stock`) ;
  - `StockServiceImp#releaseStock` (annulation, échec de paiement, remboursement, suppression
    d'une commande, commande en attente annulée par `CancelAllPendingOrdersByTimeTasklet`) ;
  - `StockServiceImp#expireReservation` (clé Redis expirée, `RedisExpirationListener`) et
    `CleanUpExpiredStockReservationsTasklet` ;
  - `StockServiceImp#resizeReservation` (une commande modifiée réserve moins).
- **Définition du « disponible »** : `stock - reservedStock`, recalculée à 3 endroits
  (`ProductManagementServiceImp#getAvailability`, `StockServiceImp` l. ~336,
  `RecommendationDigestServiceImp#isInStock`). À factoriser dans une classe `*Utils` avant d'en
  ajouter un 4e (règle « pas de duplication » du `CLAUDE.md`).
- **Briques réutilisables** : `WishlistEntity` (user + product, unique), système de notification
  (`NotificationRetryableDelivery`, handlers `@NotificationTypeHandler`, réémission par
  `REDELIVER_FAILED_NOTIFICATIONS_JOB`), Web Push (une notification par navigateur, voir
  `WebPushNotificationListener`), modèle de job (`RecommendationDigestJob*`, ShedLock).
- **Pas besoin de Kafka** (voir la discussion du 2026-11-07) : un job périodique couvre tous les
  chemins ci-dessus sans toucher au code du stock.

## Choix d'architecture proposé : job de détection, pas d'événement

Émettre un événement « retour en stock » à chaque endroit où le stock remonte obligerait à
modifier 5 chemins sensibles (verrous, Redis, idempotence) et à en oublier un tôt ou tard. À la
place, un job toutes les 10 min cherche les **alertes en attente dont le produit est de nouveau
disponible** et les envoie. Coût : jusqu'à 10 min de délai, acceptable pour ce type d'alerte.
Bénéfice : aucun changement dans `StockServiceImp`, et un retour en stock venant d'un chemin
futur est couvert d'office.

## Décisions à prendre avant de coder

1. **Déclenchement** :
   - **(a)** bouton explicite « M'alerter du retour en stock » sur une fiche produit en rupture
     (proposé : l'utilisateur demande l'alerte, donc pas de question de consentement) ;
   - **(b)** automatique pour tout produit en rupture présent dans la wishlist ;
   - **(c)** les deux (le bouton ajoute aussi à la wishlist).
2. **Canal** : email seul, ou email + Web Push (proposé : les deux, le push existe déjà et c'est
   le cas d'usage où le temps réel compte ; le push reste un « en plus » comme pour le paiement).
3. **Peu d'unités, beaucoup d'abonnés** (ex. 1 unité revenue, 50 alertes) :
   - **(a)** prévenir tout le monde, premier arrivé premier servi (proposé : simple, et l'email
     dit clairement que le stock est limité) ;
   - **(b)** prévenir au plus N × le stock disponible, par ordre d'inscription, les autres
     restent en attente.
4. **Après l'envoi** : l'alerte est terminée (proposé, une alerte = un envoi ; l'utilisateur se
   réabonne si le produit repart en rupture) ou elle reste active pour les ruptures suivantes.
5. **Durée de vie** : une alerte non déclenchée expire après 90 jours (proposé) ou jamais.
6. **Produit déjà disponible au moment de l'abonnement** : refuser (409, proposé) ou accepter
   sans effet.

Les étapes ci-dessous supposent les choix proposés ; les adapter selon les réponses.

## Étapes (suivre les patterns existants)

### 0. Factoriser le « disponible »
- `utils/ProductStockUtils.availableStock(ProductEntity)` (null-safe, borné à 0) et
  `isAvailable(ProductEntity)`. Remplacer les 3 calculs existants. Tests unitaires dédiés.

### 1. Modèle
- `entities/StockAlertEntity` (étend `BaseEntity`) : `user`, `product`, `status`
  (`entities/enums/StockAlertStatus` : `PENDING`, `NOTIFIED`, `CANCELLED`, `EXPIRED`),
  `requestedAt`, `notifiedAt`. Contrainte : une seule alerte `PENDING` par (user, product) —
  vérification applicative + index `(productId, status)` pour le job.
- `repositories/StockAlertRepository` :
  - `findPendingWithAvailableProduct(Pageable)` : alertes `PENDING` dont le produit a
    `stock - reservedStock > 0`, avec `JOIN FETCH` user et product (pas de N+1) ;
  - `existsByUser_KeycloakIdAndProduct_UuidAndStatus(...)` ;
  - `findByUser_KeycloakIdAndStatus(..., Pageable)` (liste « mes alertes ») ;
  - mise à jour en masse `EXPIRED` des alertes plus vieilles que `expire-after-days`.
- Migration `src/main/resources/sql/migrations/<date>-stock-alert.sql` + `databaseSchemaInitFile.sql`.

### 2. API utilisateur (`StockAlertController` + `StockAlertControllerApiSpec`)
- `POST /api/v1/services/stock-alerts/{productUuid}` (authentifié) : 201 ; 404
  `PRODUCT_NOT_FOUND` ; 409 `PRODUCT_ALREADY_IN_STOCK` (décision 6) ; 409
  `STOCK_ALERT_ALREADY_EXISTS` si déjà en attente.
- `DELETE /api/v1/services/stock-alerts/{productUuid}` : 204 (passe `CANCELLED`), idempotent.
- `GET /api/v1/services/stock-alerts/mine` : alertes `PENDING` paginées (produit, date).
- Exceptions custom + handlers `ErrorManagementController` + `ErrorCode`, comme
  `ReviewAlreadyExistsException`.
- Si décision 1 = (c) : `WishlistManagementService#add` appelé par le service d'alerte (pas
  l'inverse, pour garder la wishlist indépendante).
- Optionnel : `ProductAvailabilityDto` gagne `alertRequested` pour l'utilisateur connecté (le
  front sait afficher « Alerte activée ») — à voir avec le front.

### 3. Service `StockAlertService` (interface + impl)
- `subscribe`, `unsubscribe`, `findMine` (API).
- `prepareAlert(alertId)` en `REQUIRES_NEW` : relit l'alerte et le produit, revérifie
  `PENDING` + disponible + utilisateur actif, passe l'alerte `NOTIFIED` (`notifiedAt = now`) et
  renvoie le(s) `NotificationContext` (email + un par navigateur abonné). L'alerte est marquée
  **avant** l'envoi, comme pour l'email de suggestions : un job relancé ne renvoie rien.

### 4. Job `STOCK_ALERT_JOB`
- `CyberTechAppConstants.STOCK_ALERT_JOB`, `batch/job/core/StockAlertJob` +
  `implementation/StockAlertJobImp` (`@Scheduled` + `@SchedulerLock`), bean dans `BatchConfig`.
- `batch/task/StockAlertTasklet` (étend `BaseTasklet`) : expire d'abord les vieilles alertes,
  puis parcourt `findPendingWithAvailableProduct` par pages ; par alerte :
  `prepareAlert` puis `NotificationRetryableDelivery#deliver` ; un try/catch par alerte.
- Propriétés : `cybertech.stock-alert.job.activated` (vrai partout : l'utilisateur a demandé
  l'alerte), `.cron` (`0 */10 * * * *`), `.batch-size=100`, `.expire-after-days=90`.

### 5. Notification
- `NotificationType.STOCK_ALERT`, `NotificationSubject.STOCK_ALERT`
  (« [Produit] est de nouveau disponible - Cybertech » — sujet dynamique, posé par le handler),
  `EmailTemplateType.STOCK_ALERT` → `templates/email/stock-alert.html` (même mise en page que
  `recommendation-digest.html` : photo, nom, prix, lien produit, mention « stock limité »).
- `StockAlertPayload` (contact, nom, prix, image, lien produit, lien « gérer mes alertes »),
  ajouté aux `@JsonSubTypes` de `NotificationPayload` (réémission).
- Handler `StockAlertNotification` (`@NotificationTypeHandler`).
- Push : contenu court (titre + nom du produit + URL) via le processeur push existant ;
  vérifier comment `PushNotificationProcessor` construit le message par type et ajouter le cas.
- Liens front : `application.frontend.url` + `cybertech.recommendation.digest.product-path`
  (même propriété que l'email de suggestions, ne pas en créer une seconde).

### 6. Tests
- Unitaires :
  - `ProductStockUtilsTest` (null, réservé > stock, 0, positif) ;
  - service : abonnement (OK, 404, 409 en stock, 409 doublon), désabonnement idempotent,
    `prepareAlert` (disponible → `NOTIFIED` + contextes email/push ; plus disponible, déjà
    notifiée, utilisateur inactif → rien) ;
  - tasklet : pages, expiration, un échec n'arrête pas les autres ;
  - job (copie de `RecommendationDigestJobImpTest`), handler, rendu réel du template (comme
    `RecommendationDigestTemplateTest`), controller (`@WebMvcTest`, 401 anonyme), handlers
    d'erreur, tests d'enum épinglés (`NotificationType`, `NotificationSubject`,
    `EmailTemplateType`) ;
  - `CustomExceptionAdviceParityTest` / `CustomExceptionsConstructorTest` couvrent les nouvelles
    exceptions automatiquement (scan du package).
- IT `StockAlertFlowIT` (Testcontainers, capture SMTP comme les ITs de notification) :
  produit à 0 → abonnement → l'admin remet du stock → passage du job → un email, alerte
  `NOTIFIED` ; second passage → rien ; annulation d'une commande qui libère du stock déclenche
  aussi l'alerte (prouve l'intérêt du job : aucun code de stock modifié).
- `mvnw test` après chaque étape, puis l'IT.

### 7. Vérification manuelle (stack locale)
- Mettre un produit à 0 (admin), s'abonner, remettre du stock, attendre le job (ou cron
  rapproché) ; lire l'email dans Mailpit (`http://localhost:8025`) et le push dans un
  navigateur abonné.

### 8. Documentation
- `docs/stock-alert.md` (fonctionnement, délais, propriétés) ; OpenAPI (`openapi.yaml` à la
  main) ; Postman ; `progress.md`.
- À dire au front : bouton « M'alerter » sur une fiche en rupture, page « Mes alertes »,
  `npm run gen:api`.

## Hors périmètre (à décider plus tard)
- Alerte de **baisse de prix** sur la wishlist : même mécanique (job + entité), avec un prix de
  référence enregistré à l'abonnement. Bon candidat pour la suite, et premier vrai cas où un flux
  d'événements (outbox, puis éventuellement Kafka) deviendrait intéressant si les deux alertes et
  d'autres consommateurs se multiplient.
- Réservation temporaire d'une unité pour le premier alerté.
- Alerte par SMS.

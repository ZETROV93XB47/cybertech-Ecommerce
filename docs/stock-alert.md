# Alerte de retour en stock

Plan d'origine : `docs/plans/back-in-stock-alert.md`. Livré le 2026-11-07 (backend + tests
unitaires) ; IT `StockAlertFlowIT` ajoutée le même jour. Reste la vérification Mailpit / navigateur.

## Décisions (2026-11-07)

1. **Déclenchement** : bouton explicite « M'alerter » sur une fiche en rupture (pas d'abonnement
   automatique depuis la wishlist).
2. **Canal** : email + Web Push sur chaque navigateur abonné (l'email part quel que soit le canal
   favori de l'utilisateur).
3. **Peu d'unités, beaucoup d'abonnés** : tout le monde est prévenu, premier arrivé premier servi
   (l'email le dit).
4. **Une alerte = un envoi** : après l'envoi l'alerte est terminée ; l'utilisateur se réabonne
   pour la rupture suivante.
5. **Durée de vie** : une alerte jamais déclenchée expire après 90 jours.
6. **Produit déjà disponible** : 409 `PRODUCT_ALREADY_IN_STOCK`.

## API (authentifiée)

| Méthode | Chemin | Réponses |
|---|---|---|
| `POST` | `/api/v1/services/stock-alerts/{productUuid}` | 201 `StockAlertResponseDto` ; 404 `PRODUCT_NOT_FOUND` ; 409 `PRODUCT_ALREADY_IN_STOCK` / `STOCK_ALERT_ALREADY_EXISTS` |
| `DELETE` | `/api/v1/services/stock-alerts/{productUuid}` | 204 (idempotent) |
| `GET` | `/api/v1/services/stock-alerts/mine` | 200, page des alertes en attente (défaut `size=20`, `sort=requestedAt,desc`) |

« Disponible » = `stock - reservedStock > 0` (`ProductStockUtils`, même définition que le panier,
les commandes et `GET /product/availability`).

## Fonctionnement

- `StockAlertEntity` (`stockAlertTable`) : une ligne par (utilisateur, produit), statut `PENDING`,
  `NOTIFIED`, `CANCELLED` ou `EXPIRED`. Se réabonner remet la même ligne en `PENDING`. Contrainte
  unique `(userId, productId)` : deux abonnements simultanés donnent un 409, pas un doublon.
- **Détection par le job `STOCK_ALERT_JOB`** (toutes les 10 min, ShedLock `stockAlertJob`), pas
  par un événement : le stock remonte par la mise à jour admin, l'annulation, l'échec de paiement,
  le remboursement, l'expiration d'une réservation et la modification d'une commande. Lire le
  stock courant couvre tous ces chemins (et les futurs) sans toucher à `StockServiceImp`.
  Délai maximal : un intervalle du cron.
- Par alerte : `StockAlertServiceImp#prepareAlert` (transaction propre) revérifie `PENDING`,
  produit disponible et utilisateur actif, passe l'alerte en `NOTIFIED` **avant** l'envoi (un job
  relancé ne renvoie rien), puis le tasklet livre l'email et un push par navigateur via
  `NotificationRetryableDelivery` (retries, audit, réémission). Un échec n'arrête pas le job.
- Le job commence par passer en `EXPIRED` les alertes en attente de plus de 90 jours.
- Supprimer un produit ou un utilisateur supprime ses alertes (`orphanRemoval`, comme la wishlist).

## Messages

- Email : `templates/email/stock-alert.html`, sujet « Un produit que vous attendiez est de nouveau
  disponible - Cybertech » (photo, nom, prix, lien produit, mention « stock limité »).
- Web Push : `{ title: "De nouveau disponible", body: "<produit> est de nouveau en stock.", url, productUuid }`
  (`WebPushDeliveryService#sendProductToSubscription`). `url` vient de
  `cybertech.web-push.product-url-template` (`/products/{productUuid}`). Pas de `orderUuid` dans ce
  message : le Service Worker doit prendre `tag = orderUuid ?? productUuid`.

## Propriétés

```properties
cybertech.stock-alert.job.activated=true
cybertech.stock-alert.job.cron=0 */10 * * * *
cybertech.stock-alert.batch-size=100
cybertech.stock-alert.expire-after-days=90
cybertech.web-push.product-url-template=/products/{productUuid}
```

Lien produit de l'email : `application.frontend.url` + `cybertech.recommendation.digest.product-path`.

## Production

Migration à passer avant le déploiement (`ddl-auto=validate`) :
`src/main/resources/sql/migrations/2026-11-07-stock-alert.sql`.

## À faire côté front

- Bouton « M'alerter du retour en stock » sur une fiche en rupture (`POST`), état « Alerte
  activée » / annuler (`DELETE`) ; page « Mes alertes » (`GET /mine`) ; `npm run gen:api`.
- Service Worker : `tag: orderUuid ?? productUuid` pour ne pas regrouper toutes les alertes produit.

## Limites connues

- Jusqu'à 10 min de délai entre le retour en stock et l'alerte.
- Un produit revenu puis reparti en rupture entre deux passages du job ne déclenche rien.
- Le job prévient tous les abonnés même s'il n'y a qu'une unité.

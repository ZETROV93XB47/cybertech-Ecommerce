# Événements de recommandation (UserEvent → Gorse)

Les recommandations Gorse apprennent des `UserEvent` stockés dans MongoDB (`userEventsDB.user_events`).
`GorseSyncTasklet` (job `GORSE_SYNC_JOB`) les envoie à Gorse en `PUT /api/feedback`
(`FeedbackType` = nom du type, `UserId` = keycloakId, `ItemId` = uuid du produit).

## Qui écrit quoi

### Écrits par le backend, au moment où l'action est validée

| Type | Déclencheur | Pas d'événement quand |
|---|---|---|
| `PURCHASE` | passage effectif de la commande à `PAID` (webhook `payment_intent.succeeded`, `OrderPaymentConfirmationTransactionalDelegateImp`), un par produit | `POST /order/place`, paiement refusé, webhook en double ; après un complément de paiement, produit déjà compté pour cette commande (idempotence `(orderUuid, productUuid)`) |
| `ADD_TO_CART` | produit nouveau ou quantité augmentée : `POST /cart/add` (dont la fusion du panier invité à la connexion), `PATCH /cart/update` | ajout refusé (`NOT_ENOUGH_STOCK`, produit inconnu) ; quantité baissée sans disparaître |
| `REMOVE_FROM_CART` | produit disparu du panier : `PATCH /cart/remove/{uuid}`, `DELETE /cart/decreaseQuantity` jusqu'à zéro, `PATCH /cart/update`, `DELETE /cart/clear` (un par ligne) | vidage du panier par `placeOrder` (`CartService#clearCartAfterOrder`) |
| `WISHLIST_ADD` | `POST /wishlist/add/{uuid}` | produit déjà dans la wishlist (`PRODUCT_ALREADY_IN_WISHLIST`) |
| `RATING_POSITIVE` / `RATING_NEGATIVE` | avis créé ou modifié, sans commentaire : note ≥ 4 / ≤ 2 | note de 3 ; refus de modération (403) ; modération indisponible (503) |
| `REVIEW_POSITIVE` / `REVIEW_NEGATIVE` | même règle, avec un commentaire publié (remplace le `RATING_*`) | idem |

Seuils : `CyberTechAppConstants.REVIEW_POSITIVE_RATING_THRESHOLD` (4) et
`REVIEW_NEGATIVE_RATING_THRESHOLD` (2). En modification, c'est l'avis tel qu'il est publié
après la modification (note et commentaire) qui compte.

Mécanique : le service métier publie un `RecommendationSignalsEvent` dans sa transaction
(`RecommendationSignalUtils`) ; `RecommendationSignalListener` (`@Async`,
`@TransactionalEventListener(AFTER_COMMIT)`) appelle `UserEventService#recordServerSignals`.
Une action annulée ne produit donc rien, et une panne de MongoDB ne fait jamais échouer ni
ralentir l'action : log `WARN Recommendation signal not stored: type=… productUuid=… userId=<masqué>`.

Document écrit : `userId` = keycloakId, `sessionId` = `"server"`, `timestamp`, `metadata` =
`category`, `brand`, `price`, et `orderUuid` pour un `PURCHASE`.

### Envoyés par le navigateur : `POST /api/v1/events/consume-event(s)`

Types acceptés (`UserEventType#isBrowserSignal`) : `VIEW`, `CLICK`, `COMPARE`,
`SEARCH_RESULT_CLICK`, `SCROLL_DEPTH_HIGH`, `HOVER`, `SEARCH_QUERY`, `IGNORE_RECOMMENDATION`,
`BOUNCE`.

- Type produit par le backend → **400** `INVALID_REQUEST`, `fieldErrors[].field = "eventType"`
  (ou `events[i].eventType` pour un lot), rien n'est écrit.
- `productId` inconnu → **404** `PRODUCT_NOT_FOUND` sur `consume-event` ; dans un lot, l'événement
  est ignoré et compté dans `ignored`.
- `consume-events` : 1 à 50 événements (`USER_EVENT_BATCH_MAX_SIZE`), réponse **201**
  `{ "accepted": n, "ignored": m }`.
- Limite de débit : 120 POST / minute / utilisateur sur `/api/v1/events/**`, **429** + `Retry-After`.
- `userId` est toujours celui du JWT.

## Côté Gorse (`src/main/resources/docker/gorse/config.toml`)

- Positifs : `PURCHASE`, `ADD_TO_CART`, `WISHLIST_ADD`, `COMPARE`, `RATING_POSITIVE`, `REVIEW_POSITIVE`.
- Lecture : `VIEW`, `CLICK`, `SEARCH_RESULT_CLICK`.
- Gorse v0.5 n'a pas de retour négatif : `REMOVE_FROM_CART`, `RATING_NEGATIVE`, `REVIEW_NEGATIVE`
  restent dans MongoDB mais Gorse les ignore (voulu, pas un oubli).

## Limites connues

- Le batch relit les `cybertech.gorse.sync.job.lookback-minutes` (20) dernières minutes : un
  backend arrêté plus longtemps que ça ne renvoie jamais les événements écrits juste avant l'arrêt.
- Une commande annulée ou remboursée garde son `PURCHASE` dans Gorse (le retirer demanderait
  `DELETE /api/feedback/PURCHASE/{user}/{item}`).
- L'idempotence du `PURCHASE` est une vérification avant écriture (pas d'index unique) : deux
  confirmations simultanées de la même commande pourraient l'écrire deux fois ; le cas ne se
  produit pas en pratique (une seule transition vers `PAID` à la fois par commande), et Gorse
  écrase le doublon (`PUT`).

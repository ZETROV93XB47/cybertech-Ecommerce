# Backend Business Code Review — Nouveaux findings

> Generated: 2026-04-28
> Reviewer: Claude (code-reviewer agent)
> Confidence floor: 80%
> Sources cross-referenced: `progress.md` (Waves 1-6), `LAUNCH_PROGRESS.md` (Waves 7-8), `PLAN.md`, `features-fixing.md`

## Méthodologie
- **Périmètre parcouru :** `services/implementation/` (34 classes), `api/controllers/implementation/` (13 classes), classes de support : `OrderStatus`, `UserResponseDto`, `ReviewEntity`, `ShippingListener`, `OrderPaymentConfirmationEventListener`, `KeycloakUserManagementService`, `UserPersistenceService`
- **Références consultées :** `progress.md` (Waves 1–6), `LAUNCH_PROGRESS.md` (Waves 7–9), `PLAN.md`, `features-fixing.md`
- **Confiance minimum appliquée : 80 %**
- **DO NOT TOUCH respecté :** `NotificationListener.java`, `NotificationOutcomeRecorder.java`, `NotificationRepository.java`

---

## Findings — par criticité

### Critique (data-loss)

**1. `cancelOrder` autorise l'annulation en statut `AWAITING_SHIPPING` — stock définitivement perdu**
`src/main/java/com/novatech/cybertech/services/implementation/OrderManagementServiceImp.java:612,746`

- **Symptôme :** Un utilisateur peut appeler `POST /cancel` sur une commande en `AWAITING_SHIPPING` (code 5). La garde `isOrderAlreadyShipped` (ligne 746) teste `status.getCode() >= SHIPPED.getCode()` (≥ 6), laissant passer le statut 5. Or à ce stade le stock a déjà été *committed* — `OrderPaymentConfirmationEventListener.handlePaymentSuccess` a appelé `stockService.commitStock()` (ligne 73) qui a décrémenté `productEntity.stock`. L'appel `stockService.releaseStock(orderUUID)` à la ligne 639 de `doCancelOrder` est alors un no-op (aucune réservation ACTIVE en table `stockTable`).
- **Cause :** `isOrderAlreadyShipped` (ligne 746) ne bloque qu'à partir de SHIPPED. AWAITING_SHIPPING est entre PAID et SHIPPED dans l'enum `OrderStatus` (code 5 < 6), mais le stock y est déjà irréversiblement consommé.
- **Impact :** Chaque annulation d'une commande en préparation d'expédition réduit de façon permanente les stocks sans compensation. L'inventaire diverge de la réalité physique. Les remboursements Stripe sont bien émis (le `paymentAttempts` stream ligne 632 fonctionne), mais le stock ne revient jamais.
- **Fix proposé :** Soit étendre `isOrderAlreadyShipped` pour inclure `AWAITING_SHIPPING` : `status.getCode() >= AWAITING_SHIPPING.getCode()`. Soit, si l'annulation à ce stade est souhaitée, appeler `stockService.reserveStock` + `stockService.releaseStock` pour recréer puis libérer la réservation manuellement.

---

### Haute (bug fonctionnel)

**2. `ReviewManagementServiceImp.create()` ne valide pas que la commande est dans un état payé**
`src/main/java/com/novatech/cybertech/services/implementation/ReviewManagementServiceImp.java:61-88`

- **Symptôme :** Un utilisateur peut soumettre un avis en fournissant le UUID d'une commande en statut `CREATED`, `AWAITING_PAYMENT`, ou `PAYMENT_FAILED` — c'est-à-dire pour laquelle aucun paiement n'a abouti.
- **Cause :** Le check ligne 67 vérifie uniquement l'appartenance de la commande. Aucune vérification du `order.getStatus()` n'est effectuée dans `create()`, contrairement à `getReviewableProducts()` qui utilise `REVIEWABLE_ORDER_STATUSES = {PAID, SHIPPED, DELIVERED}` (ligne 124-125). La méthode `checkIfUserAlreadyBoughtThisProduct` (ligne 189) utilise intentionnellement `orderItemRepository.userHasBoughtProduct` sans filtre de statut (note Wave 2 J2 du `progress.md`), ce qui aggrave la fenêtre d'exploitation.
- **Impact :** Contournement de la règle métier "seul un acheteur confirmé peut noter un produit". N'importe quel utilisateur inscrit peut manipuler les notes produit en créant une commande non payée.
- **Fix proposé :** Ajouter après la ligne 68 :
  ```java
  if (!REVIEWABLE_ORDER_STATUSES.contains(order.getStatus())) {
      throw new OrderDoesntBelongsToUserException(
          "Cannot review order " + reviewCreateRequestDto.getOrderUuid()
          + ": order not in a reviewable state (" + order.getStatus() + ")");
  }
  ```

**3. `UserManagementServiceImp.update()` appelle Keycloak à l'intérieur d'un `@Transactional` sans compensation**
`src/main/java/com/novatech/cybertech/services/implementation/UserManagementServiceImp.java:94-106`

- **Symptôme :** Si `userRepository.save(user)` (ligne 103) échoue et que la TX rollback, l'appel préalable à `keycloakUserManagementService.updateUser()` (ligne 99) a déjà propagé les modifications (prénom, nom, email) à Keycloak — elles sont irréversibles. Keycloak et la DB divergent silencieusement.
- **Cause :** Contrairement à `create()` (fix H1, Wave 2), `update()` n'a pas été refactorisé pour séparer l'appel Keycloak (hors TX) du save DB (REQUIRES_NEW). Les deux opérations sont dans le même `@Transactional` Spring.
- **Impact :** Désynchronisation identité Keycloak / DB locale. L'email visible dans la session JWT (provenant de Keycloak) peut différer de celui en base, impactant les fonctionnalités email (commandes, notifications).
- **Fix proposé :** Déplacer `keycloakUserManagementService.updateUser(...)` APRÈS `userRepository.save(user)` (ligne 103). Ainsi, si le save DB échoue, Keycloak n'est jamais appelé (TX rollback). Si le save réussit mais Keycloak échoue, logger l'erreur et éventuellement réessayer via un event AFTER_COMMIT.

**4. `UserManagementServiceImp.deleteByUUID()` : suppression Keycloak avant DB dans un `@Transactional`**
`src/main/java/com/novatech/cybertech/services/implementation/UserManagementServiceImp.java:110-114`

- **Symptôme :** Si `userRepository.deleteByUuid(uuid)` (ligne 113) lève une exception (ex. FK violation sur commandes, cartes bancaires), la TX rollback, mais `keycloakUserManagementService.deleteUser(user.getKeycloakId())` (ligne 112) a déjà supprimé l'utilisateur de Keycloak — sans possibilité de rollback.
- **Cause :** Même anti-pattern que le Finding 3 : appel système externe (non transactionnel) avant l'opération DB dans la même TX Spring.
- **Impact :** L'utilisateur existe en base mais son compte Keycloak est supprimé. Il ne peut plus se connecter mais ses données persistent (commandes, cartes). Incohérence visible uniquement lors du prochain accès.
- **Fix proposé :** Appeler `keycloakUserManagementService.deleteUser(...)` APRÈS `userRepository.deleteByUuid(uuid)`, ou mieux via un `@TransactionalEventListener(AFTER_COMMIT)` publiant un `UserDeletedEvent(keycloakId)` depuis la TX, de sorte que Keycloak n'est touché que si le DELETE DB a été commité.

**5. `getStatusByUUID` manque le bypass admin (asymétrie avec `getByUUID`)**
`src/main/java/com/novatech/cybertech/services/implementation/OrderManagementServiceImp.java:164-178`

- **Symptôme :** Un admin appelant `GET /status/{uuid}` sur une commande qui ne lui appartient pas reçoit HTTP 403. L'endpoint équivalent `GET /get/{uuid}` a le bypass admin (K1, Wave 3) via `isCurrentCallerAdmin()` (lignes 135-140 de la même classe), mais `getStatusByUUID` ne l'a pas.
- **Cause :** Le Wave 3 (K1) a corrigé uniquement `getByUUID(UUID, String)`. `getStatusByUUID` (ligne 164) exécute le check d'appartenance inconditionnellement aux lignes 168-173.
- **Impact :** Les admins ne peuvent pas utiliser le polling `/status/{uuid}` pour monitorer les commandes des utilisateurs. Incohérence de permissions entre deux endpoints de même nature.
- **Fix proposé :** Wrapper le check d'appartenance de `getStatusByUUID` avec `!isCurrentCallerAdmin()` :
  ```java
  if (!isCurrentCallerAdmin() && (order.getUserEntity() == null || ...)) {
      throw new OrderDoesntBelongsToUserException(...);
  }
  ```

---

### Moyenne (cohérence / robustesse)

**6. `register` (public) expose le `keycloakId` dans la réponse 201**
`src/main/java/com/novatech/cybertech/api/controllers/implementation/UserManagementController.java:123-126`

- **Symptôme :** L'endpoint public `POST /register` retourne `Map.of("id", created.getUuid(), "keycloakId", created.getKeycloakId())`. Le Keycloak subject (sujet JWT) est transmis à tout client anonyme lors de l'inscription. Le `@JsonIgnore` sur `UserResponseDto.keycloakId` ne s'applique pas ici car le `Map.of` contourne la sérialisation du DTO.
- **Cause :** Résidu du fix D6 (Wave 1) qui a `@JsonIgnore` sur le DTO mais a conservé la divulgation dans ce `Map` pour que l'appelant administrateur `registerAuto` puisse récupérer le keycloakId. La route publique `/register` a reçu le même traitement par accident de copier-coller.
- **Impact :** Le Keycloak subject, normalement opaque (il sert de clé dans les appels `/cart`, `/order`, etc.), est révélé dès l'inscription. Facilite le ciblage d'utilisateurs spécifiques dans des attaques API.
- **Fix proposé :** Retirer `"keycloakId"` du `Map.of` du `register` public (ligne 125). Le conserver uniquement dans `registerAuto` (réservé ADMIN, déjà correct).

**7. `CartServiceImp.updateCart()` ne vérifie pas le stock disponible**
`src/main/java/com/novatech/cybertech/services/implementation/CartServiceImp.java:449-487`

- **Symptôme :** `updateCart()` (lignes 467-480) remplace les items du panier sans vérifier `product.getReservedStock() + quantity <= product.getStock()`. La vérification est présente dans `doAddItemsToCart` (ligne 226) mais absente de `updateCart`.
- **Cause :** `updateCart` a été ajouté comme nouveau fix BUG-026 et n'a pas répliqué le contrôle de stock de la méthode d'ajout.
- **Impact :** Un panier peut afficher des quantités supérieures au stock disponible. La détection n'interviendra qu'au moment du `placeOrder` via `stockService.reserveStock`, créant une expérience utilisateur dégradée (panier OK, commande refusée au paiement).
- **Fix proposé :** Ajouter dans la boucle `for (final CartItemAddRequestDto line : items)` de `updateCart`, après `productMap.get(line.getProductUuid())`, la vérification : `if (product.getReservedStock() + line.getQuantity() > product.getStock()) throw new NotEnoughStockException(...)`.

---

### Basse (code smell sérieux)

**8. `ReviewManagementServiceImp.saveReview()` — méthode stale sans ownership**
`src/main/java/com/novatech/cybertech/services/implementation/ReviewManagementServiceImp.java:171-186`

- **Symptôme :** Méthode `saveReview()` (ligne 172) génère une entité `ReviewEntity` via `DataGenerator.generateReviewEntity()` sans aucun utilisateur réel, sans vérification d'ownership, et la persiste en base. Vestige de développement.
- **Cause :** Non retirée lors des refactorisations. Non exposée via un endpoint connu, mais `@Transactional` et publique sur le bean Spring.
- **Impact :** En cas d'appel (test mal configuré, injection accidentelle), crée des avis orphelins potentiellement avec des FK nulles. Confusion lors de la maintenance.
- **Fix proposé :** Supprimer la méthode et son import `DataGenerator`.

---

## Faux positifs candidats

- **`DiscountCampaignAdminServiceImp.update()` — cache evicté avant commit :** `evictCache` est appelé dans la TX de `update()`. Si la TX rollback, le cache est vide mais la DB est inchangée. À la prochaine lecture, le cache se re-remplit avec la valeur DB (correcte). Pas de bug — éventuellement cohérent par design.

- **`PaymentServiceImp.processPayment()` — exception sur idempotency hit SUCCESS :** Lève `PaymentAlreadyCompletedForThisOrderException` au lieu de retourner le paiement existant. Choix défensif intentionnel documenté (F2 Wave 2) — pas un bug.

- **`cancelOrder` sur statut `PAID` :** Un ordre PAID peut être annulé (code 4 < 6). Le refund Stripe est déclenché correctement. Le stock a été committed mais `releaseStock` est un no-op — acceptable car le stock a été réellement vendu (paiement confirmé). La réservation n'existe plus, donc aucune "double libération". Différent du Finding #1 (AWAITING_SHIPPING) car à l'état PAID la préparation physique n'a pas encore commencé.

---

## Synthèse

| Criticité | Findings nouveaux |
|---|---|
| Critique (data-loss) | 1 |
| Haute (bug fonctionnel) | 4 |
| Moyenne (cohérence) | 2 |
| Basse (code smell) | 1 |
| **Total** | **8** |

### Top 3 priorités

1. **Finding #1 — Cancel sur AWAITING_SHIPPING détruit le stock** (`OrderManagementServiceImp.java:746`) : perte irréversible de stock à chaque annulation post-commit. Fix one-liner sur `isOrderAlreadyShipped`.
2. **Finding #2 — Avis sans commande payée** (`ReviewManagementServiceImp.java:65`) : règle métier centrale contournable par tout utilisateur inscrit avec une commande non payée.
3. **Finding #3/4 — Keycloak appelé dans `@Transactional` sans compensation** (`UserManagementServiceImp.java:99,112`) : désynchronisation identité silencieuse lors d'erreur DB sur update/delete.

---

**Verdict global :** Le backend Cybertech présente une base saine après les 9 vagues de fixes. Les 8 findings identifiés sont tous nouveaux (non documentés dans progress.md / PLAN.md / features-fixing.md). Le bug le plus grave (#1) est un défaut de logique dans l'enum de statut de commande qui entraîne une perte de stock non compensable. Les findings #3 et #4 reproduisent pour `update()` et `deleteByUUID()` le même anti-pattern Keycloak/TX que celui corrigé pour `create()` en Wave 2 (H1) — une opportunité de clôturer uniformément cette famille de bugs. Au total, 2 lignes de code corrigent les findings #1 et #5 ; les findings #2, #3, #4 demandent chacun moins de 10 lignes.

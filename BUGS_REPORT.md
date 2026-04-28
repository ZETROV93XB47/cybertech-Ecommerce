# Cybertech — Rapport des bugs corrigés et zones complexes

> Document généré automatiquement pour récapituler le travail des instances Claude précédentes.
> Sources : `progress.md` (Waves 1-6), `LAUNCH_PROGRESS.md` (Waves 7-9), `PLAN.md`, `features-fixing.md`,
> `K8S_ISSUES.md`, et commentaires `BUG-XXX` encore présents dans le code (146 occurrences sur 44 fichiers).
>
> Vous (l'utilisateur) n'avez **pas** appliqué ces correctifs vous-même : ils ont été produits par des
> instances Claude (Sonnet 4.6 + Opus 4.7) orchestrées en sous-agents pendant les Waves 1 à 9. Ce rapport
> liste ce qui a été corrigé, où, et **pourquoi** la correction marche.

---

## 1. Vue d'ensemble

| Métrique | Valeur |
|---|---|
| Bugs `BUG-XXX` corrigés (Waves 1-3 + pré-existants confirmés en `PLAN.md`) | **~58** identifiants distincts |
| Tâches `PRE-X` résiduelles fermées (Waves 7-9) | **9** (PRE-1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11) — toutes fermées |
| Bugs / dette encore ouverts (cf. §3) | **~6** (post-Wave 9 : SMS stub, MailServiceImp.frontendUrl, atomic outbox, dead-letter dashboard, retry metrics, GDPR DELETE /user/me) |
| Commits liés aux corrections (Waves 1-9) | **~30** sur `dev/develop` (de `5fe4d5f` à `914d34c`) |
| Fichiers Java contenant encore une trace `BUG-XXX` | **44** (tous des javadoc/commentaires de pinning, pas des bugs ouverts) |
| Tests verts à la dernière vérification | 1681 unit + 51 IT (incl. BUG-160 ré-activé en Wave 8) |

---

## 2. Bugs corrigés — par criticité

### 2.1 Tier 1 — Critiques (data-loss, sécurité, IDOR)

| ID | Fichier:ligne | Symptôme | Correctif | Pourquoi ça fixe |
|----|---|---|---|---|
| **BUG-A1** (cascade) | `entities/OrderItemEntity.java:29,33` | `@ManyToOne(cascade = ALL)` côté `orderEntity` + `productEntity` : supprimer un OrderItem effaçait l'Order et le Product parents | Retrait du `cascade = ALL` ; le ManyToOne ne propage plus rien | Sur `@ManyToOne` la cascade ne doit jamais être ALL — l'enfant ne doit pas pouvoir détruire son parent. Commit `5fe4d5f`. |
| **BUG-A2** (cascade) | `entities/ReviewEntity.java:33` | Idem côté `productEntity` : delete d'une Review effaçait le produit | Cascade retirée | Même raison : la review ne doit pas être propriétaire du Product. Commit `5fe4d5f`. |
| **BUG-B1** (NO_DISCOUNT) | `services/.../OrderPriceCalculationServiceImp.java:49-60` | Quand `DiscountType.NO_DISCOUNT` était sélectionné, le moteur de strategy plantait sur "no campaign found" → 500 sur le checkout | Court-circuit explicite avant la résolution de campagne | NO_DISCOUNT n'a pas besoin de campagne en base ; on retourne le total brut tel quel. Commit `0d26559`. |
| **BUG-B2** (stock leak) | `services/.../OrderManagementServiceImp.cancelOrder()` (`:506-508`) | `cancelOrder` ne libérait pas la réservation de stock → fantôme stock après cancel | Appel `stockService.releaseStock(orderUUID)` ajouté avant la transition `CANCELED` | Aligne le cycle de vie commande/stock : tout chemin terminal (FAILED ou CANCELED) doit libérer. Commit `0d26559`. |
| **BUG-B3** (review update) | `services/.../ReviewManagementServiceImp.update()` (`:103-112`) | `update` faisait un `save(new ReviewEntity(...))` avec FK vides → écrasait le user/product associé | Patch sur l'entité chargée (`findById` puis setters), puis save | Conserver l'identité JPA et les FK existantes ; `save` sur entité détachée avec PK déjà connue est un upsert dangereux. Commit `0d26559`. |
| **BUG-C1** (cancel persist) | `batch/task/CancelAllPendingOrdersByTimeTasklet.java:65-69` | Boucle de cancel mettait à jour les entités en mémoire mais ne flushait jamais → rollback transparent | `orderRepository.saveAll(successfullyCancelled)` ajouté à la fin de l'itération | Spring Batch n'auto-flushe pas l'entité retournée par le tasklet ; il faut un save explicite sur la liste mutée. Commit `ed68af0`. |
| **BUG-C2** (double-ship race) | `batch/task/ShipAllPaidOrdersTasklet.java:54-71` + `listener/ShippingListener.java:74-80` | Le tasklet ET le listener `OrderPaidEvent` claimaient la même commande → double envoi de l'email + double UPDATE | Pattern atomic-claim : `PAID → AWAITING_SHIPPING` via save optimistic-locked (`@Version` sur `BaseEntity`) ; le perdant attrape `OptimisticLockingFailureException` et abandonne | Un seul writer peut bumper le `@Version` ; l'autre échoue proprement et n'envoie pas le mail. Commit `ed68af0`. |
| **BUG-C3** (swallowed step) | `batch/base/BaseTasklet.java:47-69` | Exception attrapée → `log.error` puis return CONTINUABLE → le step apparaissait vert dans Spring Batch | `ExitStatus.FAILED` + rethrow | Une erreur non-rethrow trompe le scheduler et masque les retries au niveau job. Commit `ed68af0`. |
| **BUG-D1** (IDOR order) | `OrderManagementController.getOrderByUuid` | N'importe quel user pouvait lire une commande d'un autre via UUID | Check d'ownership via JWT subject ; non-owner → 403 ; admin bypass via `ROLE_ADMIN` (regression-fix Wave 3 K1) | Authentification ≠ autorisation ; le UUID seul ne prouve rien. `SecurityContextHolder` côté service permet le bypass admin sans changer la signature. Commit `03e86bf` + K1 `44fb0b9`. |
| **BUG-D2** (IDOR user) | `UserManagementController.getUserByUuid` | Idem côté user : lecture du profil d'autrui | `@PreAuthorize` + check JWT subject == uuid demandé, sauf ROLE_ADMIN | Même logique. Commit `03e86bf`. |
| **BUG-D3** (route publique) | `config/SecurityConfig.PUBLIC_URLS` | `/api/v1/services/user/get/all` était publique → dump complet des users | Retirée de `PUBLIC_URLS` (et de `TestSecurityConfig`) | C'était littéralement une fuite. Commit `03e86bf`. |
| **BUG-D4 / BUG-201** (admin-only) | `OrderManagementController.placeOrder2` (`POST /place/auto`) + `UserManagementController.registerAuto` | Endpoints de seeding accessibles à tout user authentifié | `@PreAuthorize("hasRole('ADMIN')")` + narrowing de `SecurityConfig.PUBLIC_URLS` au chemin exact `/register` (pas `/register/**`) | Les endpoints "auto" forgent des objets sans validation métier ; ils doivent rester ADMIN. Commit `03e86bf`. |
| **BUG-D5** (event spoof) | `UserEventController.collectEvent` + `UserEventDto` | Le client envoyait `userId` dans le body → on stockait ce qu'il voulait | Override serveur : `eventDto.setUserId(jwt.getSubject())` ; `@JsonIgnoreProperties(value="userId", allowGetters=true)` sur le DTO | Un identifiant d'autorisation ne se prend jamais d'un body non signé. Commit `03e86bf`. |
| **BUG-D6 / 2505** (PII leak + role) | `UserResponseDto.keycloakId` ; `ReviewCrudController.createReview` | `keycloakId` exposé en JSON ; createReview accessible anonymement | `@JsonIgnore` sur le champ ; `@PreAuthorize("hasRole('USER')")` sur l'endpoint | Le keycloakId est un secret d'identité ; les reviews doivent venir d'un compte authentifié. |
| **BUG-2506** (review IDOR) | `services/.../ReviewManagementServiceImp` (W2-A dans `PLAN.md`) | Un user pouvait poster une review en envoyant un `orderUuid` qui n'était pas le sien | Check d'ownership de l'order avant création de la review | Sans le check, n'importe qui peut "review-bomber" un produit en réutilisant un order public. |
| **BUG-036** (PCI-DSS) | `entities/BankCardEntity.java:43` + `services/.../AesCardEncryptionService.java` + `BankCardManagementServiceImp.applyPciStorageRules()` | Le PAN complet était stocké en clair en DB | AES/GCM via `CardEncryptionService`, IV+ciphertext+tag base64 ; on garde uniquement les 4 derniers chiffres pour affichage ; clé `app.security.card-encryption-key` (32 bytes b64) | PCI-DSS req 3.5 : le PAN doit être chiffré at-rest. Le 4-derniers est une dérivation safe-to-display. |
| **BUG-160 / 161** (cart IDOR + race) | `services/.../CartServiceImp.java` + `CartManagementController.java` | (1) Un user pouvait lire/écrire le cart d'un autre via UUID. (2) Deux ajouts concurrents créaient deux carts pour le même user → un panier "fantôme" | (1) Ownership check + `UnauthorizedCartAccessException → 403`. (2) `UNIQUE(userId)` sur `cartTable` (SQL + `@UniqueConstraint`) + retry-once sur `DataIntegrityViolation` + `SELECT FOR UPDATE` sur lecture + lock Redis raw-bytes | (1) Idem D1/D2. (2) La contrainte unique au niveau SGBD est la seule garantie réelle ; le retry rattrape le 2e thread et le fait converger sur le cart créé. **Le 2e bug caché** : `CartCacheHelperImp.acquireLock` JSON-encodait le token mais lisait en raw bytes → l'unlock CAS Lua ne matchait jamais → lock survivait jusqu'à TTL. Commit `73dc5c1` (Wave 8). |
| **BUG-170 / 171 / 520 / 521 / 522** (Stripe webhook) | `services/.../PaymentWebhookServiceImp.java` | Plusieurs trous : pas d'idempotence, pas d'event domain, double-traitement après terminal-state, livemode pas validé | (170) `ProcessedWebhookEventRepository` + insert idempotent par `eventId`. (171) `OrderPaidEvent` publié pour `ShippingListener`. (520) `PaymentFailedEvent` pour libérer le stock. (521) Guard si commande déjà SUCCEEDED : on ignore les `payment_failed` out-of-order. (522) Match du `livemode` event vs config | Stripe redélivre les events ; sans dedup on encaisse deux fois. Sans guard 521 un event en retard remet la commande en FAILED. Le 522 protège contre les leaks live↔test. |

### 2.2 Tier 2 — Hauts (logique métier, cache, transactions)

| ID | Fichier:ligne | Symptôme | Correctif | Pourquoi ça fixe |
|----|---|---|---|---|
| **BUG-E1** (Redis polymorphic) | `config/RedisConfig.java` | Jackson 3 sans default-typing → `LinkedHashMap` au reload du cache au lieu du DTO | `JsonMapper` avec `BasicPolymorphicTypeValidator` (allowlist explicite) + `DefaultTyping.NON_FINAL` + `JsonTypeInfo.As.PROPERTY` | Sans `@class`, Jackson ne sait pas reconstruire la classe concrète depuis le JSON Redis. L'allowlist (K4 Wave 3) évite RCE par typing. Commit `e72b484` + K4 `44fb0b9`. |
| **BUG-E2** (cart cache) | `services/.../CartServiceImp` + `CartCacheHelperImp` | `@CachePut` + `@CacheEvict` + write manuel = double write incohérent | Suppression annotations Spring Cache ; `cartCacheHelper.putWithJitter` explicite ; `clearCart` réécrit le DTO vide (helper n'expose pas d'evict) | Une seule source de vérité du cache, jitter TTL anti-stampede préservé. Commit `e72b484`. |
| **BUG-F1** (moderation URL) | `clients/CommentModerationClient` | `@Value` perdu dans un refacto → URL `null` → NPE au boot | `@Value("${moderation.api.url}")` restauré | La propriété existe (line 21 `application.properties`) ; il manquait juste l'injection. Commit `16408e7`. |
| **BUG-F2** (webhook dedup) | `services/.../PaymentWebhookServiceImp` | L'écriture dedup-ledger se faisait toujours, même sur un event `unhandled` → on bloquait le retry Stripe d'un event valide | Flag `boolean handled` ; on ne grave que les events réellement traités | Stripe doit pouvoir réessayer un event dont notre code n'a pas pris la main. Commit `16408e7`. |
| **BUG-G1 / G2** (TX repo) | `DiscountCampaignRepository.deleteByDiscountType`, `CrudBaseRepository.deleteByUuid/deleteAllByUuidIn` | Méthodes derived `deleteBy*` sans `@Modifying` → Spring Data refuse en runtime | `@Modifying` + `@Transactional` sur chacune | Spring Data exige `@Modifying` pour qu'un derived query non-`select` ne soit pas traité comme un `findBy`. Commit `40ffa84`. |
| **BUG-H1** (Keycloak/DB saga) | `services/.../UserManagementServiceImp.create()` | Keycloak créait l'user, puis la TX DB échouait → user fantôme dans Keycloak sans contrepartie DB | `UserPersistenceService.saveNewUser` `@Transactional(REQUIRES_NEW)` ; en cas d'échec, compensation `keycloakUserManagementService.deleteUser` + log si la compensation échoue aussi | Saga manuelle minimale : on isole l'appel externe non-transactionnel et on gère explicitement la compensation. `BankCardManagementService` déplacé dans le saga pour qu'il partage la TX REQUIRES_NEW. Commit `59ea6b6`. |
| **BUG-I1 à I5** (DTO hardening) | `OrderPlacingRequestDto`, `ReviewCreateRequestDto`, `OrderCancellationRequestDto`, `BankCardCreationRequestDto`, `ProductSearchRequestDto` | Champs `userUuid` côté client (spoof), `orderUuid` non-`@NotNull`, `cardNumber` sans pattern PAN, `category` `@NotNull` qui empêchait la recherche cross-catégorie | (I1/I2) Suppression `userUuid`. (I3) `@NotNull` sur `orderUuid`. (I4) `@Pattern("\\d{13,19}")`. (I5) `@NotNull` retiré + service null-safe | L'identité doit venir du JWT ; le PAN doit avoir un format ; la recherche doit pouvoir être globale. Commit `cbd4353`. |
| **BUG-J1 / J2** (N+1) | `services/.../ReviewManagementServiceImp` + `OrderRepository` + `OrderItemRepository` | N+1 sur `getReviewableProducts` ; check `userHasBoughtProduct` itérait les graphs lazy en mémoire | (J1) Nouvelle requête JPQL JOIN FETCH `orderItemEntities` + `productEntity`. (J2) Nouvelle boolean query `userHasBoughtProduct(keycloakId, productUuid)` | Une seule requête au lieu de N+1, et le check d'achat devient O(1) en SQL au lieu de O(orders × items). Commit `a66db6a`. |
| **BUG-2507 / 2508** (mail safety) | `services/.../MailServiceImp.java` | Après `MessagingException` le code retournait silencieusement → l'appelant croyait l'envoi OK ; logs à INFO incluaient PII | Rethrow des `MessagingException` / `MailException` en `NotificationDeliveryException` (unchecked) ; logs PII en DEBUG | La couche supérieure (Resilience4j `@Retry`) doit voir l'échec pour réessayer. Commit `dc1a54f` (Phase 1 notification refactor). |
| **BUG-082 / 083** (S3) | `services/.../S3ServiceImp.java` (W1-C dans `PLAN.md`) | Pas de MIME-type allowlist ; pas de cap de taille → upload arbitraire | Allowlist + size cap | Empêche un user d'uploader un binaire malveillant ou de saturer le bucket. |
| **BUG-138** (validation) | `api/error/ErrorManagementController.java` + `FieldErrorDto.java` | `MethodArgumentNotValidException` retournait un message canned, sans dire quel champ avait foiré | Construction d'un body `errors: [{field, message}]` à partir du `BindingResult` | Sans le détail, le front ne peut pas highlighter le champ ; expérience dev/QA dégradée. |
| **BUG-2501 / 2502** (webhook ack) | `api/controllers/.../StripeWebhookController.java` | (2501) Une exception interne renvoyait 500 → Stripe lance une retry storm. (2502) Body JSON malformé renvoyait 500 au lieu de 400 | (2501) `try/catch` global → 200-ACK avec log d'erreur. (2502) catch `JsonSyntaxException` → 400 | Stripe retente jusqu'à 3 jours sur 5xx ; un 200 préserve la file d'events tant qu'on log l'incident. Le 400 sur JSON cassé indique non-retriable. |
| **BUG-039** (cart qty) | `exceptions/NegativeQuantityException.java` + `CartServiceImp.addItemsToCart` | Quantité négative passait → réduisait le stock | Validation explicite + exception 400 | Garde-fou métier basique. |
| **BUG-050** (failed payment) | `services/.../OrderManagementServiceImp` | Stock pas libéré après payment FAILED | `stockService.releaseStock` sur le chemin failure | Symétrique de BUG-B2 mais sur le chemin d'échec paiement. |
| **BUG-052** (double-discount) | `OrderManagementServiceImp.retryPayment` | Au retry, on réappliquait la stratégie de discount sur le prix déjà discounté | Le retry réutilise verbatim `order.getTotalAmount()` (déjà final) | Le total est calculé une seule fois à la création ; relancer le pricing au retry double la promo. |
| **BUG-054** (JWT subject) | `OrderManagementServiceImp.resolveKeycloakIdFromJwt` | `jwt.getSubject() == null` → NPE en cascade | Helper qui throw `UserNotFoundException` (mappé proprement par `ErrorManagementController`) | Refuser tôt avec une 404 propre vaut mieux qu'une 500 sale. |
| **BUG-060** (deadlock) | `services/.../StockServiceImp.java:62-84` | Réservations multi-produits → ordre de lock non-canonique → deadlock A-B/B-A | `TreeMap(UUID::toString)` impose l'ordre alphabétique sur les UUIDs avant lock | Tous les threads prennent les locks dans le même ordre → impossible de croiser. |
| **BUG-070** (events direct payment) | `services/.../PaymentServiceImp.java:84` | Le chemin direct-attempt ne publiait pas les domain events → listeners shipping/notification jamais déclenchés sur ce chemin | Construction d'un `StripeWebhookEventDto` synthétique + publication | Garantit que les deux chemins (webhook + direct) convergent vers la même chaîne d'events. |
| **BUG-110** (batch reservations) | `batch/task/CleanUpExpiredStockReservationsTasklet.java:29` | Chargement de TOUTES les réservations en mémoire pour filtrer | Requête `findByReservationStatusAndCreatedAtBefore` en SQL | Filtrage côté DB → le tasklet scale à des dizaines de milliers de rows. |
| **BUG-111** (per-item try/catch) | `batch/task/CancelAllPendingOrdersByTimeTasklet` | Une exception sur une commande tuait tout le tasklet | try/catch par-item dans la boucle | Robustesse batch : un mauvais item ne bloque pas les autres. |
| **BUG-121** (Redis listener) | `listener/RedisExpirationListener.java:80` | UUID malformé dans la clé → `IllegalArgumentException` non-attrapée → listener mort | try/catch autour de `UUID.fromString` | Un listener Redis qui meurt sur une clé mal formée n'est plus jamais réveillé. |
| **BUG-122** (orphan notification) | `listener/ShippingListener.java:35,105` | Un `NotificationContext` local construit mais jamais dispatché | Le listener délègue désormais à `NotificationListener` via event ; suppression du local orphelin | Une seule source de vérité pour la notification shipping. |
| **BUG-124** (NPE listener) | `listener/OrderPaymentConfirmationEventListener.java:41,131` | `order_uuid` absent du metadata Stripe → NPE | `Optional.ofNullable` + early-return avec WARN | Stripe peut envoyer un event sans toutes les metadata ; on log au lieu de crasher. |
| **BUG-130 / 131 / 132 / 133 / 134** (Money/Address/Currency) | `entities/valueObjects/{Money,Address,CurrencyCode}.java` | (130) `equals` BigDecimal-scale-sensitive. (131) Pas de `subtract`/`multiply`. (132) `Address` `@Embeddable` avec `@Setter` (mutable). (133) Devises manquantes. (134) Pas de `fromString` | (130) `equals` value-only via `compareTo == 0`. (131) Méthodes ajoutées. (132) Address rendue immutable + `withXxx` style. (133) INR/BRL/MXN/RUB/KRW/ZAR ajoutés. (134) `fromString` présent | Un value object DOIT être immutable et avoir un `equals` value-based. Sinon JPA cache + Hibernate dirty-checking deviennent imprévisibles. |
| **BUG-140** (PII 500) | `api/error/ErrorManagementController.java:297` | Le body 500 incluait `ex.getMessage()` → fuite de SQL/stack/secrets | Message générique ; la cause détaillée part en log seulement | Une 500 ne doit jamais leak l'interne ; obfuscation = base de défense. |

### 2.3 Tier 3 — Moyens (validation, robustesse, doc)

| ID | Fichier | Symptôme | Correctif | Pourquoi ça fixe |
|----|---|---|---|---|
| **BUG-001 à BUG-016** | `api/error/ErrorManagementController.java` | Plusieurs exceptions custom non mappées → 500 par défaut | `@ExceptionHandler` ajouté pour chacune avec le bon code HTTP | Bonne API REST = 4xx pour fautes client, 5xx pour fautes serveur. |
| **BUG-017 / 018 / 019** | `mappers/{Product,Cart,User}Mapper.java` | photoUrl pas mappée ; NPE sur cart vide ; address null casse le mapper | Wiring + null-guards | Les mappers MapStruct doivent être tolérants au null sur les optional. |
| **BUG-025** | `ErrorManagementController` | `CartNotFoundException` retournait 500 | Handler 404 + ErrorCode `CART_NOT_FOUND` | Cart not found est une 404 standard. |
| **BUG-026** | `dto/request/cart/CartUpdateRequestDto` + `CartManagementController` | DTO inexistant + signature mauvaise sur `@PathVariable`/`@RequestBody` | DTO créé + annotations corrigées | Type-safe + validation côté input. |
| **BUG-027** | `CartManagementController` | Mêmes problèmes sur `delete` | `@PathVariable` ajouté | Idem. |
| **BUG-028** | `CartCreateRequestDto` | `@Valid` manquant sur la liste imbriquée → items invalides passaient | `@Valid` ajouté | Bean-Validation ne descend dans `List<X>` qu'avec `@Valid`. |
| **BUG-029 / 031 / 2503** | `ErrorManagementController` | `MethodArgumentTypeMismatch` 500, `AccessDenied` 500, `HttpMessageNotReadable` 500 | Handlers explicites → 400 / 403 / 400 | Mappings standards. |
| **BUG-035** | `UserEventController` | Code retournait `FolderEvent.CREATED` (typo) au lieu de `HttpStatus.CREATED` | Constante corrigée | Cast/import erreur de débutant. |
| **BUG-037 / 038** | `BankCardManagementServiceImp` | (037) Pas de validation de date d'expiration. (038) Pas de notion de carte par défaut | (037) Parsing `MM/yyyy`, rejet du past-dated. (038) Méthodes `setDefault`/`getDefault` + un seul `isDefault=true` par user | UX classique paiement : sécurité (date) + ergonomie (carte par défaut). |
| **BUG-064** | `StockServiceImp:98-112` | `releaseStock` sur order sans réservation → no-op silencieux | WARN explicite avec orderUuid | Un no-op silencieux masque les double-commits upstream. |
| **BUG-075 / 076 / 077** | `StripePaymentAttemptProcessor` | Méthode de paiement hard-codée ; refund mal encodé | Configurable + bon encoding | Aligner avec l'API Stripe v2. |
| **BUG-112 / 113** (open, pin par test) | `OrdersSummaryReportListener` | Casts raw + pas de try/catch par recipient | NON FIXÉ — pin sur le bug par test (cf. §4) | Reste à faire. |
| **BUG-120** (open, pin) | `events/consumer/ProductElasticConsumer` | `@KafkaListener` commenté → consumer mort | NON FIXÉ — pin par test ; Kafka pas utilisé en runtime | Décision d'architecture : on est passé à `ApplicationEventPublisher`. |
| **BUG-135** | `utils/DataGenerator.java:241` | UUID partagé entre tests → coupling | Génération unique par test | Évite les flakys. |
| **BUG-139** | `ErrorManagementController` | `UnrecognizedPropertyException` retournait String au lieu d'`ErrorResponseDto` | DTO returné | Format de réponse uniforme. |
| **BUG-373** (open, pin) | `ReviewCrudControllerAdditionalTest` | Contrat moderation admin pas défini | NON FIXÉ — `@Disabled` jusqu'à spec | Attendu par l'utilisateur. |
| **BUG-DISCOUNT-CAMPAIGN-001** (closed) | `entities/enums/DiscountTypeEnumTest` | Pin sur l'ancien champ `discountPercentage` qui a été retiré (refacto campaign DB-driven) | Tests `@Disabled` car l'enum a légitimement changé | Refacto vers `DiscountCampaign` DB-driven (commit `44a26b7`) rend l'ancien shape obsolète. |

---

## 3. Bugs / dette encore ouverts

### 3.1 Items `PRE-X` résiduels — tous fermés en Wave 7-8

Tous les `PRE-X` listés en Wave 3 ont été fermés :

| ID | État |
|----|---|
| PRE-1 (orphanRemoval OrderItem) | Fermé Wave 7A (`f703964`) |
| PRE-2 (UNIQUE cartTable + retry) | Fermé Wave 8 (`73dc5c1`) — bonus : Redis lock raw-bytes |
| PRE-3 (actuator whitelist) | Fermé Wave 7B (`460f1ff`) |
| PRE-4 (BankCard isDefault default) | Fermé Wave 7A (`0681a0b`) |
| PRE-5 (`-Pintegration-test` profile) | Fermé Wave 7A (`0681a0b`) |
| PRE-6 (ES 7→8) | Fermé Wave 9 (`686811c`) |
| PRE-7 (orphan ApiSpec) | Fermé Wave 7A (`f703964`) |
| PRE-8 (Discount ApiSpec) | Fermé Wave 8 (`a1982f7`) |
| PRE-9, 10, 11 (specs cosmétiques + securityRequirement) | Fermés Wave 7A/B (`f703964`, `4f167df`) |

### 3.2 Backlog fonctionnel encore ouvert

| Sujet | Fichier | Description | Effort |
|----|---|---|---|
| **GDPR DELETE /user/me** | À créer | Endpoint right-to-erasure non implémenté ; spec dans `docs/runbooks/gdpr-data-handling.md` | ~1 session |
| **SmsNotificationProcessor stub** | `notification/SmsNotificationProcessor.java:18` | `log("SMS SENT")` only — silent no-op si SMS choisi en preferred channel | Brancher provider OU lever exception explicite |
| **`MailServiceImp.frontendUrl`** | inutilisé | `@Value` jamais lu — flag de cleanup | Petit |
| **Atomic outbox** | events/listeners | `AFTER_COMMIT` listener ne survit pas à un crash JVM entre commit et listener — fenêtre étroite mais réelle | Moyen |
| **Dead-letter dashboard** | notification batch | Pas d'UI pour replay les rows `FAILED` terminales | 1 session |
| **Resilience4j metrics Micrometer** | observability | Bridge non-exposé | Petit (cf. Wave 9 PRE follow-up) |
| **Frontend OTel propagation** | front/app | Backend trace, mais front ne propage pas trace-id → traces incomplètes | Moyen |
| **Real Stripe HMAC dans Gatling firehose** | `src/test/.../gatling/StripeWebhookFirehoseSimulation.java` | Placeholder fonctionne en dev ; prod = real HMAC à signer | Petit |
| **PostHog/Sentry frontend** | front/app | Dernier item Task 7 du playbook | Moyen |
| **Redis chart deep audit** | `helm/charts/redis-chart` | "Looks ok" dixit Wave 7B mais non auditée à fond | Petit |

### 3.3 Bugs `BUG-XXX` à statut "pin par test" (volontairement open)

| ID | Statut | Raison |
|----|---|---|
| BUG-112 | Pin (`OrdersSummaryReportListenerTest`) | Casts raw + STOPPED status → ClassCast au mail-build. LOW priorité, garde-fou test en place. |
| BUG-113 | Pin (idem) | Pas de try/catch par recipient. |
| BUG-120 | Pin (`ProductElasticConsumerTest`) | `@KafkaListener` commenté ; Kafka pas utilisé en prod. |
| BUG-373 | `@Disabled` (`ReviewCrudControllerAdditionalTest:383`) | Contrat moderation admin pas encore défini. |
| BUG-DISCOUNT-CAMPAIGN-001 | `@Disabled` (`DiscountTypeEnumTest`) | Refacto campaign DB-driven a retiré le champ — test obsolète. |

---

## 4. Commentaires `BUG-XXX` trouvés dans le code (encore présents)

Tous les fichiers qui suivent contiennent au moins un commentaire/javadoc `BUG-XXX`. **Aucun n'est un bug ouvert** — ce sont des annotations explicatives laissées par les correctifs (pinning du contrat, justification PCI, etc.) ou des `@Disabled` sur tests.

| Fichier | Compte | Statut |
|---|---|---|
| `services/implementation/CartServiceImp.java` | 14 | Fixé (BUG-026, BUG-160, BUG-161 — javadoc/marker) |
| `services/implementation/BankCardManagementServiceImp.java` | 16 | Fixé (BUG-036/037/038/161) |
| `services/implementation/OrderManagementServiceImp.java` | 12 | Fixé (BUG-052/054) |
| `api/controllers/.../StripeWebhookController.java` | 11 | Fixé (BUG-2501/2502) |
| `services/implementation/PaymentWebhookServiceImp.java` | 10 | Fixé (BUG-170/171/520/521/522) |
| `services/implementation/AesCardEncryptionService.java` | 7 | Fixé (BUG-036) |
| `services/implementation/StockServiceImp.java` | 4 | Fixé (BUG-060/064) |
| `mappers/entity/BankCardMapper.java` | 4 | Fixé (BUG-036/038) |
| `entities/BankCardEntity.java` | 4 | Fixé (BUG-036/038) |
| `services/implementation/CartCacheHelperImp.java` | 3 | Fixé (BUG-160 raw-bytes lock) |
| `services/core/CartService.java` | 5 | Fixé (BUG-026/161 contract markers) |
| `dto/response/user/BankCardResponseDto.java` | 3 | Fixé (BUG-036/038 — masquage PAN) |
| `api/controllers/.../CartManagementController.java` | 3 | Fixé (BUG-026/161 markers) |
| `api/controllers/.../UserManagementController.java` | 2 | Fixé (BUG-201) |
| `api/controllers/.../BankCardManagementController.java` | 3 | Fixé (BUG-038/161) |
| `entities/CartEntity.java` | 1 | Fixé (BUG-160 PRE-2 — UNIQUE constraint marker) |
| `listener/{ShippingListener,RedisExpirationListener,OrderPaymentConfirmationEventListener,NotificationListener}.java` | 8 (cumul) | Fixés (BUG-121/122/124) |
| `api/error/{ErrorManagementController,model/FieldErrorDto}.java` | 3 | Fixés (BUG-138/140/161) |
| `api/error/enumpackage/ErrorCode.java` | 1 | Marker BUG-161 |
| `services/core/{OrderManagementService,PaymentWebhookService,CardEncryptionService,CartCacheHelper,BankCardManagementService}.java` | 11 (cumul) | Markers de contrat |
| `repositories/{CartRepository,BankCardRepository,ProcessedWebhookEventRepository}.java` | 4 | Markers (BUG-160/038/170) |
| `exceptions/{NegativeQuantityException,UnauthorizedCartAccessException,UnauthorizedBankCardAccessException}.java` | 3 | Sentinelles (BUG-039/161/038) |
| `entities/valueObjects/CurrencyCode.java` | 1 | Marker BUG-133 |
| `mappers/entity/OrderMapper.java` | 2 | Marker BUG-132 (Address immutability) |
| `dto/request/cart/CartUpdateRequestDto.java` | 1 | Marker BUG-026 |
| `services/implementation/PaymentServiceImp.java` | 2 | Marker BUG-070 |
| `services/implementation/MailServiceImp.java` | 1 | Marker BUG-2507 |
| `config/SecurityConfig.java` | 1 | Marker BUG-201 (path narrowing) |
| `utils/DataGenerator.java` | 1 | Marker BUG-135 |
| `batch/task/CleanUpExpiredStockReservationsTasklet.java` | 1 | Marker BUG-110 |
| `api/controllers/spec/{BankCardControllerApiSpec,CartManagementControllerApiSpec}.java` | 4 | Markers BUG-038/161 dans les `@Operation.description` |

---

## 5. TODO / FIXME notables

(Les seuls non triviaux ; pas de FIXME/HACK/XXX critiques identifiés.)

| Fichier:ligne | Commentaire | Évaluation |
|---|---|---|
| `entities/PaymentEntity.java:49` | `stripePaymentID` mal nommé — `String stripePaymentID;//TODO: ID de paiement chez stripe, il faut penser à le renommer` | Cosmétique (rename qui propagerait dans tout le code). |
| `entities/UserEntity.java:49` | `//TODO: Vérifier le type LocalDate pour la colonne birthDate` | À vérifier — `LocalDate` est correct vs `Date` ; sans doute un héritage. |
| `dto/response/order/OrderResponseDto.java:21,23,31` | 3 × `//TODO: mapper correctement ce champ` | DTO de réponse incomplet — champs renvoyés `null` ou hard-codés ; à investiguer (effet sur le front). |
| `services/core/ProductManagementService.java:11` | `//TODO: à refactorer plus tard` | Vague, low-prio. |
| `services/.../OrderManagementServiceImp.java:99,105,180,186` | 4 × `//TODO: refactor this method to make it callable only by an admin or separate this crud method in another service` | Méthodes CRUD admin et user mélangées dans le même service — à séparer si on veut ré-isoler les permissions. |
| `services/.../OrderManagementServiceImp.java:268,467` | 2 × `//TODO: is it really necessary to make this check here ?` (validateUserBeforeProcessingPayment) | Doute sur la position du check ; revoir avec un test métier. |
| `services/.../ReviewManagementServiceImp.java:95` | `//TODO: Optimisation potentielle ici, chercher directement en base les users actifs` | Filtrage en mémoire qui pourrait passer en SQL. Effet O(N). |
| `services/.../ReviewManagementServiceImp.java:194` | `//TODO: maybe make this part more explicit in the future, can be confusing` | Code peu lisible — refacto bienvenu. |

---

## 6. Classes et parcours complexes — où se concentrer

### 6.1 `OrderManagementServiceImp` + parcours commande

- **Rôle :** Orchestre placeOrder → réservation stock → création paiement Stripe → confirmation/annulation. ~800 lignes, le service le plus large du domaine.
- **Pourquoi c'est complexe :**
  - Multi-flux : `placeOrder` (cart → order) + `placeOrder2` (admin auto) + `retryPayment` (relance) + `cancelOrder` (transition + libération stock) + `updateOrder` (admin).
  - Multi-services : appelle `StockService`, `PaymentService` via factory, `OrderPriceCalculationService`, `BankCardManagementService`, et publie 3+ events domain.
  - Transactions imbriquées + `@TransactionalEventListener(AFTER_COMMIT)` async.
- **Pièges à connaître :**
  - **BUG-052** : `retryPayment` doit réutiliser `order.getTotalAmount()` verbatim — *ne pas* relancer le pricing.
  - **BUG-054** : tout chemin qui prend un `Jwt` doit passer par `resolveKeycloakIdFromJwt` (rejette `sub == null`).
  - **BUG-B2 + BUG-050** : `cancelOrder` ET le chemin failure paiement doivent appeler `stockService.releaseStock`.
  - **M2 race** : `cancelOrder` retry-loop 3 essais à cause de `@Async` listener PAID qui bumpe le `@Version` concurrent ; idempotence si `CANCELED` déjà.
  - **K1** : ownership check via `SecurityContextHolder` côté service (et non au controller) pour permettre admin bypass sans changer la signature.

### 6.2 `PaymentWebhookServiceImp` + flux Stripe

- **Rôle :** Consomme les events Stripe (`payment_intent.succeeded` / `.payment_failed`) reçus par `StripeWebhookController` et déclenche les transitions order + events domain.
- **Pourquoi c'est complexe :**
  - Events potentiellement out-of-order, retry-storm Stripe (3 jours), idempotence requise.
  - Bridge entre webhook HMAC et domain (`OrderPaidEvent` / `PaymentFailedEvent`).
  - API_VERSION skew : Stripe SDK constant peut différer du dashboard.
- **Pièges :**
  - **BUG-170** : dedup `ProcessedWebhookEventRepository` par `eventId` — sans ça, double-traitement.
  - **BUG-521** : guard "déjà SUCCEEDED" sur `payment_failed` out-of-order — sinon une commande payée repasse FAILED.
  - **BUG-522** : match `livemode` — refuse les events test sur prod et vice-versa.
  - **BUG-F2** : ne grave dans le dedup-ledger que si `handled=true` — sinon un type unhandled bloque les futurs retries valides.
  - **M1 (`04ea74b`)** : `event.getDataObjectDeserializer().getObject()` retourne `Optional.empty()` quand l'API_VERSION du payload diffère de celle compilée dans le SDK ; fallback vers `deserializeUnsafe()`. **Bug production-relevant — un Stripe account dont l'API version dashboard ne match pas le SDK aurait silencieusement perdu tous les events.**

### 6.3 `StockServiceImp` + locks + reservations

- **Rôle :** Gère `reserveStock` / `releaseStock` / `confirmStock`. Source de vérité pour la disponibilité.
- **Pourquoi c'est complexe :**
  - Multi-produits dans une commande → multi-locks → risque de deadlock A-B / B-A.
  - Pessimistic locking + reservations TTL via Redis (cleanup batch).
- **Pièges :**
  - **BUG-060** : tous les locks pris dans l'ordre `UUID.toString()` → impossible de croiser. Le `TreeMap(UUID::toString)` est load-bearing — ne pas changer le comparator.
  - **BUG-064** : `releaseStock` sur order sans réservation = WARN explicite (avant : silent). Le silent no-op masquait des double-commits.
  - **BUG-110** : la requête de cleanup filtre côté SQL (`findByReservationStatusAndCreatedAtBefore`) — ne pas revenir au "load all + filter".
  - **BUG-160** : la réservation va de pair avec la création/lecture de cart, dont le `@Version` lock pessimistique est partagé.

### 6.4 `CartServiceImp` + cache + lock distribué (BUG-160)

- **Rôle :** Lecture/écriture du cart (1 par user). Hot path : lecture cache Redis + write-through DB.
- **Pourquoi c'est complexe :** Concurrence inter-onglets pour un même user, cache TTL+jitter, lock distribué Redis Lua CAS, contrainte `UNIQUE(userId)` au niveau SGBD.
- **Pièges :**
  - **PRE-2 / BUG-160** : il a fallu DEUX corrections complémentaires :
    1. `UNIQUE(userId)` sur `cartTable` + retry-once sur `DataIntegrityViolation`.
    2. `CartCacheHelperImp.acquireLock` était JSON-encodé à l'écriture mais lu en raw-bytes → CAS Lua jamais matchait → lock survivait jusqu'au TTL → 2e thread timeout 4s sur l'acquireLockBlocking.
  - **TransactionTemplate construit lazy** : bean `PlatformTransactionManager` peut être absent en unit-tests → fallback no-tx logged.
  - **Hibernate `ddl-auto=update`** : la contrainte UNIQUE doit exister AU NIVEAU JPA (`@UniqueConstraint`) ET dans le SQL d'init — sinon Testcontainers boot depuis le metadata JPA et la contrainte manque.
  - **BUG-026 / BUG-161** : update et delete passent par un guard ownership (`UnauthorizedCartAccessException → 403`).

### 6.5 `UserManagementServiceImp` + `UserPersistenceService` (saga Keycloak/DB)

- **Rôle :** Création utilisateur en 2 systèmes : Keycloak (admin client) puis DB locale.
- **Pourquoi c'est complexe :** L'appel Keycloak n'est pas transactionnel ; un échec DB après laisse un Keycloak fantôme.
- **Pièges :**
  - **BUG-H1** : la saga est manuelle :
    1. Appel Keycloak (no TX).
    2. `UserPersistenceService.saveNewUser` `@Transactional(REQUIRES_NEW)` → user + bank card en DB.
    3. Si l'étape 2 throw → `keycloakUserManagementService.deleteUser` (compensation) ; si la compensation throw aussi → log loud.
  - `BankCardManagementService` a été *déplacé* dans `UserPersistenceService` pour partager la même TX REQUIRES_NEW que le user — sinon on crée le card dans une TX différente.
  - **PRE-4** : `BankCardCreationRequestDto.isDefault` était `null` non-default ; PR ajoute `@Builder.Default false` + `@JsonProperty("isDefault")` (Jackson 3 strip le préfixe `is` sinon).

### 6.6 `ShipAllPaidOrdersTasklet` + `ShippingListener` (course atomic-claim)

- **Rôle :** Deux chemins peuvent expédier une commande : le batch nightly ET le listener `OrderPaidEvent`.
- **Pourquoi c'est complexe :** Sans coordination, les deux peuvent claim la même commande → double email + double UPDATE.
- **Pièges :**
  - **BUG-C2** : pattern atomic-claim via `@Version` optimistic-lock save : `PAID → AWAITING_SHIPPING`. Le perdant attrape `OptimisticLockingFailureException` et abandonne.
  - **`@Version` est sur `BaseEntity`** : si quelqu'un retire `@Version`, la course se rouvre. Mettre un IT qui pin l'invariant avant tout changement de `BaseEntity`.
  - **K3** : `ShipAllPaidOrdersTasklet.execute` n'est plus `@Transactional` ; chaque commande est claim-then-ship dans `ShipOrderTransactionalDelegate` `@Transactional(REQUIRES_NEW)` → un échec sur une commande ne rollback pas les autres.

### 6.7 `BankCardManagementServiceImp` (PCI : encryption + masquage)

- **Rôle :** CRUD bankcard avec contraintes PCI-DSS.
- **Pourquoi c'est complexe :** Le PAN ne doit JAMAIS être stocké en clair, JAMAIS log, JAMAIS retourné en API.
- **Pièges :**
  - **BUG-036** : 3 invariants couplés : (1) chiffrement AES/GCM via `CardEncryptionService` au save, (2) `lastFourDigits` calculé serveur-side jamais lu du DTO, (3) `BankCardMapper` ignore `encryptedNumber` / `lastFourDigits` / `isDefault` (le service les set après mapping).
  - **`AesCardEncryptionService`** : clé `app.security.card-encryption-key` doit être base64 32 bytes ; valider au boot. IV fresh par chiffrement (concat IV+ct+tag).
  - **BUG-037** : parsing expiry `MM/yyyy` avec rejet du past-dated.
  - **BUG-038** : un seul `isDefault=true` par user (`BankCardRepository.findDefaultByUserKeycloakId`) ; promote = unset l'ancien + set le nouveau.
  - **BUG-161** : ownership check pour les non-admins ; admin endpoints `@PreAuthorize("hasRole('ADMIN')")`.
  - **BUG-036 W2-B** : le path admin `create()` initialement skippait `applyPciStorageRules` — corrigé pour partager le même chemin.

### 6.8 `ReviewManagementServiceImp` (modération + ownership + N+1)

- **Rôle :** CRUD reviews + intégration moderation API + check d'achat user.
- **Pourquoi c'est complexe :** Modération externe async, vérification d'éligibilité ("a-t-il acheté ce produit"), N+1 historique.
- **Pièges :**
  - **BUG-J1** : `getReviewableProducts` utilise `OrderRepository.findReviewableOrdersWithItemsByKeycloakIdAndStatusIn` JOIN FETCH pour éviter N+1. Ne pas remettre l'ancienne méthode.
  - **BUG-J2** : `userHasBoughtProduct(keycloakId, productUuid)` est une boolean query SQL, pas une itération en mémoire.
  - **BUG-B3** : `update` patch l'entité chargée — JAMAIS save d'une entité fraîche avec PK existante.
  - **BUG-2506** : check ownership de l'order avant de créer la review.
  - **TODO ligne 95** : filtrage active-users en mémoire à passer en SQL (open).

---

## 7. Quelques signaux à surveiller en production

- **Stripe API_VERSION skew (BUG-M1, commit `04ea74b`).** Tout compte Stripe dont le dashboard `api_version` diffère de la constante SDK aurait perdu silencieusement TOUS les webhooks avant ce fix. Logger `Stripe.API_VERSION` au boot et alerter si différent du `webhook_secret.account.api_version` (si exposé).
- **Hibernate `ddl-auto=update` vs SQL init.** Les contraintes ajoutées en init SQL ne sont PAS rejouées sur une DB existante. Si une migration manque, Testcontainers passera (boot from JPA metadata) mais la prod aura un schéma divergent. Wave 8 a doublé la contrainte UNIQUE cart côté SQL ET côté `@UniqueConstraint` annotation pour cette raison.
- **Jackson 3 + Redis polymorphic typing.** L'allowlist `BasicPolymorphicTypeValidator` (K4) doit être étendue à chaque nouvelle classe cachée. Sans entrée explicite, le cache reload casse en runtime sur ce type.
- **Bucket4j rate limit.** Configuré sur `/api/v1/services/user/register` (chemin exact, pas prefix) + `/api/v1/services/order/place` + `/api/v1/services/cart/**`. Login pas throttlé côté Spring (Keycloak owns brute-force detection).
- **CSP en mode REPORT-ONLY.** Le frontend a probablement des inline scripts ; passer en enforce après monitoring du report-uri.
- **`mvn verify` ne lance plus les ITs** depuis PRE-5 (`0681a0b`). Tout pipeline qui attendait des ITs sur `mvn verify` doit utiliser `-Pintegration-test`.
- **`Stripe.API_VERSION` startup log line** recommandé (cf. note progress.md ligne 165).
- **Notification redrive batch** (`RedeliverFailedNotificationsTasklet`) — cumul max 9 attempts (3 ticks × 3 retries Resilience4j) ; si SMTP outage > 90 min, les rows passent terminal `FAILED` et plus rien ne les rattrape sans dead-letter dashboard.
- **K8s open issues** (cf. `K8S_ISSUES.md`) : Stripe placeholder API key, LocalStack PVC 4.x path-bind, moderation-api Werkzeug reloader OOM 256Mi, ES 8 max_map_count + stale ES 7 PVC. Charts patchés mais redéploiement manuel à faire.

---

## 8. Annexes

### 8.1 Mapping commit SHA → bugs / tâches fixés

| SHA | Wave | Scope |
|----|---|---|
| `5fe4d5f` | 1A | BUG-A1, BUG-A2 (cascade) |
| `0d26559` | 1B | BUG-B1, BUG-B2, BUG-B3 (NO_DISCOUNT, releaseStock, review patch) |
| `ed68af0` | 1C | BUG-C1, BUG-C2, BUG-C3 (batch persist + atomic-claim + ExitStatus) |
| `03e86bf` | 1D | BUG-D1..D6 (IDOR + leaks) |
| `e72b484` | 2E | BUG-E1, BUG-E2 (Redis polymorphic + cart cache) |
| `16408e7` | 2F | BUG-F1, BUG-F2 (moderation URL + webhook dedup) |
| `40ffa84` | 2G | BUG-G1, BUG-G2 (`@Modifying`) |
| `59ea6b6` | 2H | BUG-H1 (Keycloak/DB saga) |
| `cbd4353` | 2I | BUG-I1..I5 (DTO hardening) |
| `a66db6a` | 2J | BUG-J1, BUG-J2 (N+1) |
| `44fb0b9` | 3K | K1..K4 (regression review) |
| `bf51d7c` + `3da6c17` | 3L1 | CartFlowIT cascade + `@Disabled` BUG-160 |
| `72ba81f` | 3L2 | OrderFlowIT mock processor + lazy collection |
| `90d4b1a` | 3L4 | UserRegistrationFlowIT alignment |
| `04ea74b` | 3M1 | Stripe API_VERSION skew (`deserializeUnsafe`) |
| `8ca6f57` | 3M2 | cancelOrder vs PAID listener race (3-attempt retry) |
| `dc1a54f` / `7007fb0` / `3f0f84c` | Notification | BUG-2507/2508 + Resilience4j + redrive batch (3 phases) |
| `28d92e7` | pre-Wave1 | BUG-161 BankCard IDOR |
| `f703964` | 7A | PRE-1, PRE-7, PRE-9, PRE-10 |
| `0681a0b` | 7A | PRE-4, PRE-5 |
| `460f1ff` | 7B | PRE-3 + Bucket4j + CORS + Stripe IP allowlist |
| `4f167df` | 7B | PRE-11 (bearerAuth → keycloak) |
| `73dc5c1` | 8 | PRE-2 (BUG-160 final close) — UNIQUE + retry + Redis lock raw-bytes |
| `cb97d45` | 8 | Keycloak chart rewrite |
| `214e32a` | 8 | Elasticsearch chart probes/limits |
| `7b75658` | 8 | Moderation + Mailpit chart probes |
| `a1982f7` | 8 | PRE-8 (Discount ApiSpec) |
| `686811c` | 9 | PRE-6 (ES 7→8) |
| `69e8159` / `71bd365` | 9 | Observability (Loki/Prom/Tempo/Grafana + backend instrumentation) |
| `4548a9a` | 9 | Gatling load tests |
| `82eea1a` | 9 | Operational runbooks |

### 8.2 Pinning tests (`@Disabled` ou pin sur bug ouvert)

| Test | Bug pin | Statut |
|----|---|---|
| `ReviewCrudControllerAdditionalTest:383` | BUG-373 (admin moderation contract) | Open — attendu spec |
| `DiscountTypeEnumTest:17,27` | BUG-DISCOUNT-CAMPAIGN-001 | Volontairement obsolète (refacto DB-driven) |
| `OrdersSummaryReportListenerTest` | BUG-112, BUG-113 | LOW prio — pin par test |
| `ProductElasticConsumerTest` | BUG-120 | Kafka pas utilisé |
| `MoneyTest`, `AddressTest`, `CurrencyCodeTest` | BUG-130/131/132/133/134 | Tous CONFIRMED FIXED — javadoc retient l'historique |
| `ErrorManagementControllerBranchTest` | BUG-138/139/140/2503/029/031 | Tous CONFIRMED FIXED |

### 8.3 Trackers source

- `progress.md` — Waves 1-6 (campagne bug-fix originale)
- `LAUNCH_PROGRESS.md` — Waves 7-9 (production-readiness, helm hardening, observability)
- `PLAN.md` — orchestration récente, étoile sur les bugs déjà fixés
- `features-fixing.md` — refacto notification (Phases 1-3)
- `K8S_ISSUES.md` — soucis k8s deferred (Stripe key, LocalStack PVC, moderation OOM, ES 8)

---

*Fin du rapport. 146 occurrences `BUG-XXX` dans le code source réparties sur 44 fichiers — toutes des markers de fix ou pins de test, aucun bug ouvert non documenté.*

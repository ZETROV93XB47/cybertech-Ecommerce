# Plan à exécuter : email de suggestions produits (Gorse → utilisateur)

> Pour lancer : « Exécute `docs/plans/recommendation-digest-email.md` ». Plan écrit le 2026-10-22,
> pas encore commencé.

## Constat (2026-10-22)

- **Ce qui est en place côté Gorse.**
  - Envoi des données : `GORSE_SYNC_JOB` (`GorseSyncTasklet`, toutes les 15 min) pousse produits, utilisateurs et retours.
  - Lecture à la demande : `GET /recommendations/my-recommendations` (`RecommendationServiceImp#getRecommendedProducts(userId, n)`, cache Redis 15 min) suit la cascade suggestions perso → derniers produits Gorse → meilleures ventes.
- **Ce qui manque pour l'email** :
  - un job qui parcourt les utilisateurs ;
  - un `NotificationType` dédié (aujourd'hui : `ORDER_CONFIRMATION`, `ORDER_UPDATE`, `SHIPPING_CONFIRMATION`, `PAYMENT_CONFIRMATION`) ;
  - un template sous `templates/email/` ;
  - un consentement utilisateur.
- **Table existante jamais utilisée** : `recommendationTable` (`RecommendationEntity` : user, product, score, generatedAt). Aucun repository ni service.
- **Gorse n'a encore entraîné aucun modèle** (`MatchingModelFitTime` = 0001-01-01 ; seulement 2 utilisateurs avec des retours au 2026-10-22). Tant que c'est le cas, la cascade renvoie des derniers produits ou des meilleures ventes, pas des suggestions personnalisées.

## Décisions à prendre avant de coder

1. **Fréquence** : hebdomadaire (proposé : lundi 10 h, `Europe/Paris`) ou quotidienne.
2. **Consentement** : opt-in explicite (proposé) ou envoi à tous les utilisateurs actifs. Un email commercial demande en principe un consentement (RGPD / ePrivacy), avec un lien de désinscription dans chaque email.
3. **Utilisateur sans suggestion personnalisée** (Gorse vide ou repli) :
   - **(a)** ne rien envoyer (proposé : un email « best-sellers » générique a peu de valeur) ;
   - **(b)** envoyer les meilleures ventes ;
   - **(c)** envoyer seulement si Gorse a renvoyé des suggestions personnalisées.

   Le choix détermine si la cascade doit indiquer la source de ses résultats (voir l'étape 3).
4. **Nombre de produits par email** : 4 ou 6 (proposé : 6). On exclut les produits déjà achetés, ceux déjà dans le panier et ceux en rupture de stock.

## Étapes (suivre les patterns existants)

### 1. Consentement et désinscription
- `UserEntity` : `recommendationEmailOptIn` (boolean, défaut `false`) + `recommendationEmailUnsubscribeToken` (UUID).
- Migration SQL `src/main/resources/sql/migrations/<date>-recommendation-email.sql` (colonnes nullable / défaut).
- `PATCH /user/me` (`UserSelfUpdateRequestDto`) : exposer l'opt-in ; `GET /user/me` le renvoie.
- `GET /api/v1/services/recommendations/unsubscribe/{token}` (public, whitelist `SecurityConfig`) : désactive l'opt-in. Désinscription par **POST** (plus sûr face aux préchargeurs de liens des clients mail) : à trancher.
- Exception custom + handler `ErrorManagementController` pour un token inconnu (404).

### 2. Mémoire des envois (anti-doublon, éviter de répéter les mêmes produits)
Réutiliser `recommendationTable` : une ligne par produit envoyé (`user`, `product`, `score`, `generatedAt` = date d'envoi).
- `RecommendationRepository` : `existsByUser_IdAndGeneratedAtAfter` (garde « déjà envoyé cette semaine ») et `findProductIdsSentSince(userId, since)` (exclure les produits envoyés les 4 dernières semaines).
- Garde transactionnelle : écrire les lignes **avant** l'envoi, dans la même transaction que la sélection, pour qu'un job relancé ne renvoie pas l'email.

### 3. Sélection des produits
- Nouveau `RecommendationDigestService` (interface + impl) qui :
  - appelle `GorseClient#getRecommendations(keycloakId, n * 3)` ;
  - filtre : actifs, en stock (`stock - reservedStock > 0`), pas déjà achetés (commandes PAID et au-delà), pas dans le panier, pas envoyés récemment ;
  - garde les `n` premiers et les charge en un seul `findAllByUuidIn`.
- Selon la décision 3 : ne **pas** réutiliser tel quel `RecommendationServiceImp`, qui masque le repli. Exposer la source (`PERSONALIZED` / `LATEST` / `BEST_SELLERS`), ou appeler `GorseClient` directement.
- Résilience : `gorseRead` (circuit breaker déjà configuré). Gorse indisponible → l'utilisateur est sauté (log WARN), le job continue.

### 4. Job Spring Batch `RECOMMENDATION_DIGEST_JOB`
- `CyberTechAppConstants.RECOMMENDATION_DIGEST_JOB`, `batch/job/core/RecommendationDigestJob` + `implementation/RecommendationDigestJobImp`, sur le modèle de `GorseSyncJob` / `GorseSyncJobImp`.
- `batch/task/RecommendationDigestTasklet` (étend `BaseTasklet`).
- Utilisateurs : actifs, opt-in, email présent. Pagination par lots (`cybertech.recommendation.digest.batch-size=100`), un try/catch par utilisateur comme `CancelAllPendingOrdersByTimeTasklet` : un échec n'arrête pas le lot.
- Propriétés : `cybertech.recommendation.digest.job.activated` (`false` par défaut en dev/test), `.cron` (`0 0 10 * * MON`, zone `Europe/Paris`), `.products-per-email=6`, `.min-days-between-emails=7`.
- Verrou anti-exécution concurrente : même mécanisme que les jobs existants (voir le correctif « job lancé en N exemplaires » du 2026-10-05).

### 5. Notification et email
- `NotificationType.RECOMMENDATION_DIGEST`, `NotificationSubject` + `EmailTemplateType` correspondants.
- Payload `RecommendationDigestPayload` : prénom, liste (nom, photo, prix, lien produit front), URL de désinscription.
- Handler `@NotificationTypeHandler` sur le modèle de `PaymentConfirmationNotification`. Envoi par `NotificationRetryableDelivery` (retry Resilience4j, ligne `NotificationEntity`, reprise par `REDELIVER_FAILED_NOTIFICATIONS_JOB`).
- Template `templates/email/recommendation-digest.html` (Thymeleaf, même mise en page que `payment-confirmation.html`). URL du front par propriété (`cybertech.front.base-url`, à créer si elle n'existe pas).
- Canal : email seulement (pas de SMS ni de push pour du marketing), quel que soit `favoriteCommunicationChanel`.

### 6. Tests
- Unitaires :
  - sélection (filtres stock, achetés, panier, récents ; Gorse vide ; Gorse en erreur) ;
  - tasklet (opt-out sauté, déjà envoyé cette semaine, un utilisateur en échec n'arrête pas les autres) ;
  - handler et payload ;
  - désinscription (token valide, inconnu) ;
  - `CustomExceptionAdviceParityTest` / `CustomExceptionsConstructorTest` pour la nouvelle exception.
- IT `RecommendationDigestFlowIT` :
  - Testcontainers + faux Gorse (`MockRestServiceServer`, comme `RecommendationSignalsFlowIT`) + capture SMTP (GreenMail ou JavaMailSender mocké selon ce qu'utilisent les ITs de notification) ;
  - 2 utilisateurs (opt-in / opt-out) → un seul email, produits attendus, lignes `recommendationTable` écrites ;
  - second passage dans la semaine → aucun email ;
  - lien de désinscription → opt-in à `false`.
- `mvnw verify` (gate JaCoCo) puis les ITs touchés.

### 7. Vérification manuelle (stack locale)
- Activer le job avec un cron rapproché, opt-in d'un utilisateur ayant des retours, lancer.
- Lire l'email dans **Mailpit** (`http://localhost:8025`) : produits, prix, images RustFS, liens, désinscription.
- Prérequis pour des suggestions vraiment personnalisées : que Gorse ait entraîné un modèle, donc assez d'utilisateurs avec des retours. Sinon, tester avec le repli selon la décision 3.

### 8. Documentation
- `docs/recommendation-events.md` : section « Email de suggestions ».
- OpenAPI + Postman : opt-in sur `/user/me`, endpoint de désinscription.
- `progress.md` : entrée en section 2 ; à dire au front (case à cocher dans le profil, `npm run gen:api`).

## Hors périmètre (à décider plus tard)
- Suivi des ouvertures / clics (pixel, liens tracés) → `CLICK` vers Gorse.
- Email déclenché par un événement (« produit de ta wishlist en promo ») plutôt que périodique.
- Internationalisation du template.

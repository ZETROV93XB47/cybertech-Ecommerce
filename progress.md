# Progress — Cybertech E-Commerce Backend

_Dernière mise à jour : 2026-09-26 — branche `develop_back`, à jour avec `origin/develop_back`._

---

## 1. SITTREP

Backend Spring Boot 4 / Java 26 d'une plateforme e-commerce (portfolio, pas enterprise-grade). Une review fonctionnelle complète du projet a été menée par domaine (panier, commande, paiement, bankcard, notifications), plusieurs bugs réels ont été corrigés, puis une branche locale divergente (30 commits d'écart avec `origin/develop_back`, plusieurs recouvrant les mêmes fixes) a été reconciliée : les bugs déjà réglés en amont n'ont pas été retouchés, tout le reste a été mergé, et les apports locaux ont été conservés quand ils apportaient un plus par rapport à `origin`.

État de l'arbre : propre, aucun fichier suivi en attente. Un seul fichier non suivi à la racine (`cybertech-realm-export.json`, export Keycloak de référence avec secrets masqués — volontairement non commité).

Dernière validation complète connue : `./mvnw compile` → `test-compile` → suite unitaire complète (`-DskipITs`) → suite d'intégration complète (`-Pintegration-test`), toutes vertes (1966 tests unitaires, 55 tests d'intégration, 0 échec). Aucune modification de code source depuis cette validation.

Le focus explicite du propriétaire du projet : fiabiliser en priorité le parcours nominal `placeOrder` / `updateOrder` / `cancelOrder`. Tout ce qui est surface admin, nettoyage S3, réconciliation de la saga Keycloak est accepté comme non-critique pour un projet portfolio et volontairement laissé de côté sauf demande explicite.

---

## 2. Tâches faites / tâches suivantes

### Faites (cette phase de travail)

- **Double-débit sur `retryPayment`** : garde ajoutée contre un nouveau paiement si un `PaymentAttempt` `SUCCESS`/`PROCESSING` existe déjà pour la commande (`OrderManagementServiceImp`, exception `PaymentAlreadyCompletedForThisOrderException`).
- **`updateOrder` pouvait passer en `PAID` gratuitement** : garde `OrderNotFundedException` ajoutée sur la branche zéro-delta avant `stockService.commitStock`.
- **Remboursement partiel traité comme total** : corrigé côté paiement (`454de8c`), champ dérivé `refundedAmount` exposé sur `OrderResponseDto` (`63b8905`, `f71ffcb`).
- **Race d'oversell sur réservation de stock expirée** : commit du stock au lieu de le relâcher si la commande liée est déjà payée (`d8c3a3a`).
- **`isDefault` sur bankcard jamais initialisé** : concept retiré entièrement (une seule carte par user), 9 fichiers touchés + tests, `NoDefaultBankCartSetException` supprimée, `deleteByUUIDs` mort nettoyé (`50608a2`, `2064678`).
- **`cancelOrder` ignorait un refus de remboursement Stripe** : tous les remboursements éligibles sont désormais tentés et vérifiés AVANT toute mutation du statut de commande / stock ; un refus lève `OrderRefundFailedException` et annule proprement la cancellation (`OrderCancellationTransactionalDelegateImp`).
- **Cache Redis du panier écrit avant le commit DB** : corrigé sans event ni `@TransactionalEventListener` (choix explicite du propriétaire — code séquentiel direct). Étendu aux 5 chemins d'écriture panier (`add`, `remove`, `decrease`, `clear`, `update`) via le split `CartWriteTransactionalDelegate` (commit-before-unlock) + écriture du cache par l'appelant après retour de la transaction (`5544e1d`).
- **Wishlist doublon → 500** : vérifié dans le code, déjà géré correctement en amont (409 via `DataIntegrityViolationException` → `ProductAlreadyInWishlist`). Pas une régression, pas d'action nécessaire.
- Review des scénarios **Gatling** (`BrowseToOrderSimulation`, `FlashSaleSimulation`, `StripeWebhookFirehoseSimulation`) et de leur pertinence métier.
- Rédaction d'un **manuel de lancement backend-only** (Docker Compose, découverte que le realm Keycloak s'importe automatiquement via `src/main/resources/keycloak/import/cybertech-realm.json`, config IntelliJ, Postman).
- Discussion d'architecture actée : le lock Redis sur `addItemsToCart` reste justifié — pas pour la perf, mais parce que le panier doit rester lisible dans la même transaction ACID MySQL que la commande au moment du checkout (migration DynamoDB étudiée et écartée pour cette raison).
- Inventaire de toute la documentation du projet (voir liste dans la conversation / `docs/`, `jenkins/README.md`, `LOCAL_LAUNCH_CHECKLIST.md`, etc.).

### À faire / pistes ouvertes (non traitées, non priorisées)

- **Gap découvert pendant la review Gatling** : aucune gestion dédiée de `ObjectOptimisticLockingFailureException` sur le chemin panier (`ErrorManagementController` n'a pas de handler) → une collision de version sur une ligne de panier existante remonte en 500 brut au lieu d'un 409 propre. Candidat de fix simple, pas encore fait.
- **`FlashSaleSimulation` / `BrowseToOrderSimulation`** utilisent un unique `AUTH_TOKEN` statique pour tous les VUs — le `userFeeder` de `BrowseToOrderSimulation` est chargé mais jamais référencé. Invalide en partie la prémisse "multi-utilisateurs" des deux scénarios, en particulier `FlashSaleSimulation` qui est censé tester la contention stock inter-acheteurs et teste en réalité la contention panier d'un seul compte.
- Inscription avec email en doublon → 500 (devrait être 409).
- Protection anti-doublon sur les reviews produit : absente.
- Indexation Elasticsearch avant commit DB (ordre à revoir).
- Changement de catégorie produit sans revalidation des attributs associés.
- Sort du branch de sauvegarde `claude/session-2026-09-25-fixes` (contient les 7 commits désormais supersédés par la reconciliation) : à garder ou supprimer, décision non prise.

### Explicitement mis de côté (sur décision du propriétaire du projet — ne pas traiter sans demande explicite)

- Statut `FAILED` non revisité sur la saga outbox Keycloak — à regarder par un humain.
- Nettoyage des images orphelines S3.
- Étendre le lock Redis à d'autres opérations panier que `addItemsToCart` — jugé too much complexité pour le gain.
- Complétude de la surface admin en général.
- Endpoint RGPD / droit à l'effacement — confirmé non implémenté, pas une priorité.
- Review de Gorse / des campagnes de discount — jamais fait, pas demandé.

---

## 3. Remarques

- **Philosophie de merge utilisée** : ne jamais retoucher un bug déjà résolu côté `origin`, merger le reste, garder les apports locaux uniquement quand ils dépassent ce qu'`origin` proposait. À reproduire si une nouvelle divergence de branche survient.
- **Panier / cohérence transactionnelle** : la vraie raison de garder le panier en MySQL (plutôt qu'un KV/DynamoDB plus simple pour l'incrément atomique) est que `placeOrder` lit le panier via `user.getCartEntity()` dans la même transaction JPA que la création de commande et le décrément de stock (`OrderManagementServiceImp` ~ligne 498). Sortir le panier de MySQL introduirait un problème de cohérence cross-store exactement au point du système où la rigueur compte le plus. Ne pas relancer cette discussion sans revérifier ce couplage.
- **Pattern "split deux beans" pour commit-before-unlock** : utilisé pour `CartWriteTransactionalDelegate` — une méthode externe non-transactionnelle tient un lock (ou fait du post-traitement) et appelle une méthode `@Transactional` sur un bean SÉPARÉ, pour garantir que le commit a lieu avant que le lock ne soit relâché (le proxy transactionnel Spring ne s'active qu'au franchissement d'une frontière de bean). Mirroring de l'idiome déjà existant `ShipOrderTransactionalDelegate`. Réutilisable si un nouveau besoin similaire apparaît ailleurs.
- **Rejet explicite du pattern events pour le cache panier** : le propriétaire du projet préfère du code séquentiel direct et lisible à `@TransactionalEventListener(AFTER_COMMIT)` pour ce cas précis, malgré ce pattern étant la convention ailleurs dans le projet. Ne pas réintroduire des events sur ce chemin sans redemander.
- **Portée du projet** : c'est un portfolio, pas un système enterprise. Éviter de re-proposer des corrections pour des races hypothétiques à 1 chance sur 1 000 000, ou de la complexité additionnelle sur des chemins non-nominaux (admin, S3, Keycloak outbox) sauf demande explicite.

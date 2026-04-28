# Cybertech — Etat des tests et coverage

> Genere le 2026-04-28 sur la branche `dev/develop` (HEAD `914d34c`).
> Toolchain : `JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`, `mvnw.cmd 3.9.9`, JaCoCo 0.8.14.
> Perimetre : `src/main/java/com/novatech/cybertech` (147 classes apres exclusions du pom).
> Mode : unit tests seulement (`-DskipITs`). Les ITs n'ont PAS ete executes (pas de Testcontainers / Docker).

---

## 1. Build status

- Commande : `./mvnw.cmd test -DskipITs` (avec `JAVA_HOME=jdk-26.0.1`)
- Resultat global : **1696 tests run, 0 failures, 0 errors, 18 skipped**
- **BUILD SUCCESS** (exit 0)
- Temps : 53.7 s
- Issues bloquantes : aucune.
- Note Java : le `mvnw.cmd` doit imperativement etre lance avec JDK 26 (les classes compilees sont en class file v70). Avec le `JAVA_HOME=jdk-25` par defaut du poste, la compile passe mais le fork surefire echoue. Documenter dans le README de dev.

Detail des 18 skipped (capture du log surefire) :
- `ReviewCrudControllerAdditionalTest` : 1 skipped (BUG-373 admin moderation contract pin)
- `DiscountTypeEnumTest` : 2 skipped (BUG-DISCOUNT-CAMPAIGN-001 — champ `discountPercentage` retire de l'enum)
- `Scanner sanity` (test parametre dans `CustomExceptionConstructorContractTest` ou similaire) : 15 skipped — ce sont les exceptions de la liste qui n'ont pas le constructeur attendu, c'est un filtre attendu, pas un bug.

---

## 2. Couverture globale (JaCoCo, post-unit-tests)

| Metrique     | Couvertes / total | % | Gate (`-Pintegration-test`) | Status |
|---|---|---|---|---|
| Instructions | 11 744 / 13 285 | **88.40%** | — | OK |
| **Branches** | **525 / 670** | **78.36%** | **80%** | **FAIL** (-1.64 pt) |
| **Lines**    | **2 521 / 2 880** | **87.53%** | **80%** | OK (+7.53 pt) |
| Complexity   | 853 / 1 021 | 83.55% | — | OK |
| Methods      | 619 / 683 | 90.63% | — | OK |

**Lecture cle :**
- En unit-tests-seuls le gate BUNDLE BRANCH 80% est en-dessous (78.36 %) — le commentaire pom (`bundle line 92.48%, branch 91.19%` apres Wave 6) reflete la couverture **avec les ITs**, pas sans. Le profil `integration-test` execute `PaymentWebhookFlowIT`, `OrderFlowIT`, etc. qui couvrent precisement les classes a 0 % en unit-only (voir section 4).
- Pour valider le 80% il faut donc `./mvnw.cmd verify -Pintegration-test` (necessite Docker + Testcontainers).

---

## 3. Distance vers 90% (unit-tests seuls)

| Mesure | Valeur |
|---|---|
| Lignes couvertes | 2 521 |
| Lignes manquees | 359 |
| Total lignes mesurees | 2 880 |
| Lignes a couvrir en plus pour 90% line | **~71 lignes** |
| Branches couvertes | 525 |
| Branches manquees | 145 |
| Branches a couvrir en plus pour 90% branch | **~78 branches** |

90 % est tres atteignable, **mais** : 188 lignes manquees (~52% du total manque) appartiennent a 3 classes payment/crypto qui n'ont aucun test unitaire :
`PaymentWebhookServiceImp` (86), `StripePaymentAttemptProcessor` (63), `AesCardEncryptionService` (39). Couvrir ces 3 ramene line ~94% sans rien toucher d'autre.

---

## 4. Top 20 classes sous-testees (priorite pour 90%)

Tri par lignes manquees decroissantes, hors classes generees / DTOs / config (deja exclues du pom).

| Classe | Lines covered | Lines missed | % line | % branch | Effort estime |
|---|---:|---:|---:|---:|---|
| `services.implementation.PaymentWebhookServiceImp`              |   0 | 86 |   0% |   0% | **L** — service Stripe webhook (336 LOC), 36 branches a couvrir, ~12-15 tests |
| `services.implementation.StripePaymentAttemptProcessor`         |   0 | 63 |   0% |   0% | **M** — 175 LOC, 11 branches, ~6-8 tests |
| `services.implementation.AesCardEncryptionService`              |   0 | 39 |   0% |   0% | **S** — 138 LOC AES-GCM, 8 branches, 4-6 tests round-trip + erreurs |
| `services.implementation.OrderManagementServiceImp`             | 245 | 35 | 87.5%| 68.9%| **M** — 23 branches manquees sur 74 (admin bypass / cancel race / discount) |
| `services.implementation.CartCacheHelperImp`                    |  25 | 23 | 52.1%|  50%| **S** — 209 LOC mais beaucoup de fallback paths a tester ~5 tests |
| `batch.base.BaseTasklet`                                        |   3 | 20 | 13.0%| 100%| **S** — 77 LOC abstrait, peut etre exerce indirectement par les tasklets concretes |
| `services.implementation.CartServiceImp`                        | 167 | 15 | 91.8%| 73.5%| **S** — 12 branches manquees |
| `services.implementation.ReviewManagementServiceImp`            |  61 | 12 | 83.6%| 76.7%| **S** — 7 branches sur 30 manquees |
| `clients.CommentModerationClient`                               |   1 | 10 |  9.1%| 100%| **S** — client HTTP, mock RestTemplate / WebClient, 3-4 tests |
| `services.implementation.NotificationRetryableDeliveryImp`      |  10 | 10 | 50.0%|   0%| **S** — 2 branches non couvertes, 4-5 tests retry |
| `utils.UuidFormatter`                                           |  10 |  8 | 55.6%| 100%| **S** — utilitaire 55 LOC, methodes statiques |
| `batch.task.ShipOrderTransactionalDelegate`                     |   1 |  4 | 20.0%| 100%| **S** — petite delegate TX (53 LOC), 2-3 tests |
| `services.implementation.DiscountCampaignServiceImp`            |  38 |  4 | 90.5%| 100%| **XS** — 1 path edge |
| `batch.task.RedeliverFailedNotificationsTasklet`                |  53 |  4 | 93.0%| 91.7%| **XS** — chemins erreur isoles |
| `services.implementation.DiscountCampaignAdminServiceImp`       |  43 |  3 | 93.5%| 83.3%| **XS** |
| `api.controllers.implementation.BankCardManagementController`   |  11 |  3 | 78.6%| 100%| **XS** — 1-2 tests d'erreur |
| `services.implementation.NotificationOutcomeRecorder`           |  37 |  2 | 94.9%| 72.7%| **XS** — branche manquee (DO NOT TOUCH selon `LAUNCH_PROGRESS.md`) |
| `listener.OrderPaymentConfirmationEventListener`                |  27 |  2 | 93.1%| 100%| **XS** |
| `services.implementation.IdempotencyKeyServiceGeneratorImpl`    |  11 |  2 | 84.6%| 100%| **XS** |
| `mappers.entity.OrderMapper`                                    |  28 |  2 | 93.3%| 62.5%| **S** — 6 branches manquees sur 16 (mapping conditionnel) |

---

## 5. Classes critiques metier sous-testees

Filtre : packages `services/implementation`, `api/controllers/implementation`, `events/listener`, `batch/task`, `clients`, `factory`, `strategy`. Ce sont les classes qui comptent vraiment pour la qualite produit.

| Classe | LinePct | BranchPct | Risque metier |
|---|---:|---:|---|
| `PaymentWebhookServiceImp` | 0% | 0% | **CRITIQUE** — webhook Stripe (idempotence + dedup ledger Wave 2 F2 + Wave 3 M1 API_VERSION). Couvert uniquement par `PaymentWebhookFlowIT`. |
| `StripePaymentAttemptProcessor` | 0% | 0% | **CRITIQUE** — capture/refund/cancel via Stripe. Aucun test unitaire isolant les chemins d'erreur Stripe. |
| `AesCardEncryptionService` | 0% | 0% | **HAUT** — chiffrement PAN BUG-036 PCI-DSS. Doit avoir un test unitaire round-trip + cles invalides. |
| `OrderManagementServiceImp` | 87.5% | 68.9% | **HAUT** — 23/74 branches non-couvertes (cancel retry M2, admin bypass K1, NO_DISCOUNT B1, etc.). |
| `CartCacheHelperImp` | 52.1% | 50% | **MOYEN** — Redis put/evict/fallback (Wave 2 E1+E2). Branches manquees autour de la TTL jitter et du mode cache-disabled. |
| `ReviewManagementServiceImp` | 83.6% | 76.7% | **MOYEN** — 7 branches manquees, en partie sur le path "produit pas dans la commande courante". |
| `CommentModerationClient` | 9.1% | 100% | **BAS-MOYEN** — wrapper HTTP, simple a couvrir mais 9 lignes/41 manquees. |
| `NotificationRetryableDeliveryImp` | 50% | 0% | **DO NOT TOUCH** (LAUNCH_PROGRESS.md ligne 95 : refactor notification en flight cote user). A confirmer avant d'y toucher. |
| `ShipOrderTransactionalDelegate` | 20% | 100% | **MOYEN** — fix Wave 3 K3, devrait etre couvert par `ShipAllPaidOrdersTaskletTest`. |
| `BankCardManagementController` | 78.6% | 100% | **BAS** — 1 endpoint d'erreur a couvrir |
| `OrderMapper` | 93.3% | 62.5% | **BAS-MOYEN** — mapping conditionnel non exerce |

---

## 6. Tests `@Disabled` / skippes

Total : **3** annotations `@Disabled` actives sur des methodes de test (sur 6 fichiers ou le mot apparait — les autres occurences sont en commentaires/javadoc).

| Test | Raison (BUG-XXX) | Statut suggere |
|---|---|---|
| `DiscountTypeEnumTest.discountTypeShouldHavePercentage` | BUG-DISCOUNT-CAMPAIGN-001 — champ `discountPercentage` retire | **A SUPPRIMER** — le test pin un contrat mort. Remplacer par un test sur `DiscountCampaignInitializer` (deja existant). |
| `DiscountTypeEnumTest.canonicalDiscountMapping` | BUG-DISCOUNT-CAMPAIGN-001 — meme refactor | **A SUPPRIMER** |
| `ReviewCrudControllerAdditionalTest.shouldAllowAdminToDeleteAnotherUsersReview_BUG_373_desiredBehaviour` | BUG-373 — contrat admin moderation indefini | **GARDER** — pin propre du contrat futur, le compagnon `should*_currentBehaviour` est deja vert. |

Les 15 skipped supplementaires viennent de `Scanner sanity` dans `CustomExceptionConstructorContractTest` ou similaire — ce sont des cas filtres par un `assumeTrue` (exceptions sans constructeur standard). C'est par design, pas un test casse.

---

## 7. Tests qui echouent

Aucun. Build vert.

---

## 8. Recommandations pour atteindre 90% line + branch

**A. Quick wins en unit-tests (~1 jour, +5 a +7 points line, +8 a +12 points branch)**

- [ ] **Ecrire `AesCardEncryptionServiceTest`** : 4-6 tests (round-trip happy path, IV unique entre 2 calls, cle invalide, base64 corrompu, taille de cle != 32). **+39 lignes, +8 branches.**
- [ ] **Ecrire `CommentModerationClientTest`** : mock `RestTemplate`/`WebClient`, 3-4 tests (200 OK, 4xx, 5xx, timeout). **+10 lignes.**
- [ ] **Ecrire `UuidFormatterTest` complet** : 8 lignes manquees, methodes statiques pures. **+8 lignes.**
- [ ] **Ecrire `BaseTaskletTest`** ou couvrir indirectement via les tasklets concretes existantes (le hook `setExitStatus` Wave 3 C3 doit etre exerce). **+20 lignes.**
- [ ] **Ecrire `ShipOrderTransactionalDelegateTest`** : 2-3 tests (succes, exception remontee, retry idempotent). **+4 lignes.**
- [ ] **Ecrire `CartCacheHelperImpTest`** existe mais ne couvre que 52 % — etoffer avec branches TTL jitter / cache-disable / null-safe. **+15-20 lignes, +4 branches.**

**B. Coverage moyen-effort (~2-3 jours, +3 a +5 points)**

- [ ] **`StripePaymentAttemptProcessorTest`** : mocker Stripe SDK (PaymentIntent.create / capture / cancel), 6-8 tests sur les chemins de succes + erreurs `StripeException`. **+63 lignes.**
- [ ] **`OrderManagementServiceImp` branches manquees** : 23 branches a 68.9% — cibler retry M2 (3 tentatives + idempotence cancel), admin bypass K1, NO_DISCOUNT B1. ~5-7 tests additionnels. **+~25 branches.**

**C. Coverage gros effort (~3-5 jours, +3 a +4 points)**

- [ ] **`PaymentWebhookServiceImpTest`** : la classe la plus critique. Mocker Stripe `Event.GSON.fromJson` + `EventDataObjectDeserializer`, tester chaque case du switch (charge.succeeded / payment_intent.succeeded / payment_intent.payment_failed / payment_intent.canceled / charge.refunded / refund.failed) avec/sans dedup ledger. ~12-15 tests. **+86 lignes, +36 branches.**

**D. Hygiene**

- [ ] Supprimer les 2 `@Disabled` `DiscountTypeEnumTest.*` (contrat mort).
- [ ] Une fois 90% atteint sur unit-only, **monter le `<minimum>` du gate JaCoCo de 0.80 a 0.90** dans `pom.xml:570` et `pom.xml:575`. Supprimer le `${jacoco.gate.skip}=true` par defaut OU laisser et basculer le profil `integration-test` a 0.90.
- [ ] Documenter dans le README dev : **toujours lancer maven avec JDK 26** (sinon class file v70 incompatible).
- [ ] Decision : faire passer le check `BUNDLE BRANCH >= 0.80` aussi en mode `mvn verify -DskipITs` ? Aujourd'hui sans IT le branch tombe a 78.36 %. Soit on ajuste le seuil unit-only a 0.78, soit on ajoute des UT pour les classes payment/crypto.

---

## 9. Recommandation orchestrateur (agents a dispatcher)

Ordre suggere pour un budget 2h-session :

| # | Agent | Cible | Sortie attendue | Gain |
|---|---|---|---|---|
| 1 | **Quick wins crypto + utils** | `AesCardEncryptionServiceTest` + `UuidFormatterTest` (etoffer) + `CommentModerationClientTest` | 3 nouveaux fichiers test, ~12-15 tests | +57 lignes, +8 branches |
| 2 | **Stripe payment processor** | `StripePaymentAttemptProcessorTest` | 1 fichier, 6-8 tests, mocks Stripe SDK | +63 lignes, +11 branches |
| 3 | **Cart cache helper** | etendre `CartCacheHelperImpTest` existant | +5 tests | +20 lignes, +4 branches |
| 4 | **OrderManagementServiceImp branches** | etendre tests existants — cibler les 23 branches manquees | +6 tests | +0-5 lignes, +20 branches |
| 5 | **Stripe webhook (gros)** | `PaymentWebhookServiceImpTest` complet | 1 fichier, 12-15 tests, mocks Stripe `Event` deserialization | +86 lignes, +36 branches |
| 6 | **Batch base + delegate** | `BaseTaskletTest` + `ShipOrderTransactionalDelegateTest` | 2 fichiers, 5-6 tests | +24 lignes |
| 7 | **Cleanup `@Disabled` morts** | suppression 2 tests obsoletes `DiscountTypeEnumTest` | -2 skipped | clarifie le rapport |
| 8 | **Bump gate** | `pom.xml` line+branch 0.80 -> 0.90, run `mvn verify -Pintegration-test` pour confirmer | 1 commit | enforcement |

**Apres agents 1-3** : line ~92 %, branch ~85 %.
**Apres agents 1-5** : line ~96 %, branch ~91 % — objectif 90 % atteint sur les deux axes.

---

## Annexes

- Rapport HTML : `target/site/jacoco/index.html`
- CSV brut : `target/site/jacoco/jacoco.csv` (147 lignes)
- Log surefire : `test_run.log` (genere par cette analyse, ~7700 lignes)
- pom JaCoCo plugin : `pom.xml:513-583`
- Profil ITs : `pom.xml:707-735`

---

## Resume (< 250 mots)

Couverture actuelle (unit-tests seulement, sans Testcontainers) : **88.40% instructions, 87.53% lignes, 78.36% branches**, 90.63% methodes — sur 1 696 tests verts, 18 skipped (3 `@Disabled` reels, le reste est filtre legitime). **Build vert** (BUILD SUCCESS, 53 s) une fois le `JAVA_HOME` pointe sur JDK 26 (le defaut JDK 25 du poste echoue avec class file v70).

**Pour atteindre 90 % line, il manque ~71 lignes ; pour 90 % branch, ~78 branches.** 188 lignes manquees (52% du gap) viennent de **3 classes payment/crypto sans aucun test unitaire** : `PaymentWebhookServiceImp` (86), `StripePaymentAttemptProcessor` (63), `AesCardEncryptionService` (39) — couvertes uniquement par les ITs Stripe (`PaymentWebhookFlowIT`, `OrderFlowIT`).

**Top 5 classes prioritaires** (ordonnees par ratio gain/effort) :
1. **`AesCardEncryptionService`** — 39 lignes, 8 branches, ~4 tests round-trip AES-GCM (PCI-DSS BUG-036).
2. **`StripePaymentAttemptProcessor`** — 63 lignes, 11 branches, mock Stripe SDK.
3. **`PaymentWebhookServiceImp`** — 86 lignes, 36 branches, 12-15 tests sur le switch d'events Stripe (idempotence + dedup ledger).
4. **`OrderManagementServiceImp`** — 23 branches manquees sur 74 (cancel retry, admin bypass, NO_DISCOUNT).
5. **`CartCacheHelperImp`** — 23 lignes, 4 branches Redis fallback / TTL jitter.

Le gate JaCoCo BUNDLE 80 % se declenche uniquement avec `-Pintegration-test`. La doc pom (`bundle line 92.48 %, branch 91.19 %` post-Wave 6) reflete la combinaison UT + IT, pas le run unit-only. Pour passer le gate sans IT et viser 90 %, il faut absolument couvrir au moins les agents 1, 2, 3 et 5 listes en section 9.

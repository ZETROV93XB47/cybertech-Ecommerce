# Plan

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

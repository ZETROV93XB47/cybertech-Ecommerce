# Carte enregistrée chez Stripe

Le numéro de carte ne transite jamais par le backend. Le navigateur saisit la carte dans Stripe
Elements (iframe Stripe) ; le backend ne connaît que des identifiants Stripe et les données
d'affichage (marque, 4 derniers chiffres, expiration, titulaire).

## Flux

1. `POST /api/v1/services/bank-card/setup-intent` (USER/ADMIN) : crée le Customer Stripe de
   l'utilisateur à la première demande (email, nom, métadonnée `userUuid`, clé d'idempotence par
   utilisateur), puis un SetupIntent `usage=off_session`, `payment_method_types=[card]`.
   Réponse `201 { setupIntentId, clientSecret, publishableKey }`.
2. Le front initialise Stripe.js avec `publishableKey` et confirme le SetupIntent avec
   `clientSecret` (`stripe.confirmCardSetup`).
3. `POST /api/v1/services/bank-card/add` `{ setupIntentId }` : le SetupIntent doit appartenir au
   Customer de l'appelant (sinon 403 `ACCESS_DENIED`) et être `succeeded` (sinon 400
   `INVALID_CARD_SETUP`). Le backend lit le PaymentMethod chez Stripe, l'attache si besoin, en fait
   le moyen par défaut du Customer et l'enregistre. Une seule carte par utilisateur : une nouvelle
   carte remplace l'ancienne, détachée chez Stripe après commit. L'ancien corps (`cardNumber`...)
   est refusé en 400.
4. `PUT /bank-card/update` : titulaire et expiration, chez Stripe puis en base. `DELETE` (utilisateur
   ou admin) détache le PaymentMethod chez Stripe après commit.

## Paiement

`place`, `retry-payment` et le complément d'`update` créent un PaymentIntent avec `customer`,
`payment_method` de la carte, `payment_method_types=[card]`, `off_session=true`, `confirm=true`.
La métadonnée `order_uuid` permet aux webhooks `payment_intent.*` de retrouver la commande.
Un refus (`CardException`, ou statut `requires_payment_method` / `requires_action`) donne une
tentative `FAILED` puis `PAYMENT_FAILED` sur la commande ; `place` renvoie toujours 201. Le
`decline_code` est loggé, jamais le numéro.

Sans carte utilisable : 404 `BANK_CARD_NOT_FOUND` à `place` et à `retry-payment` (vérifié avant la
réservation du stock). Le devis (`quote`) n'exige pas de carte.

Cartes de test : 4242 4242 4242 4242 acceptée ; 4000 0000 0000 0341 enregistrée puis refusée ;
4000 0000 0000 9995 fonds insuffisants ; 4000 0025 0000 3155 authentification exigée, donc refusée
(pas de redirection possible hors session).

## Configuration

- `stripe.publishable-key` (`STRIPE_PUBLISHABLE_KEY`, `vault.stripe.publishable-key` en prod) :
  clé `pk_test_...` du même compte que `stripe.api.key`. Absente : `setup-intent` répond 503
  `PAYMENT_PROVIDER_NOT_CONFIGURED`.
- `stripe.payment-method` (carte de test fixe) est supprimée.

## Migration

`src/main/resources/sql/migrations/2026-10-14-stripe-saved-card.sql` : colonnes
`userTable.stripeCustomerId` et `bankCardTable.stripePaymentMethodId`, purge des cartes
enregistrées avant (numéro chiffré, impossibles à migrer vers Stripe sans renvoyer leur numéro),
suppression de `encryptedNumber`. Tant que le script n'a pas tourné, ces cartes sont traitées comme
absentes.

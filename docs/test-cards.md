# Cartes de test : réussir ou faire échouer un paiement

Projet portfolio : aucune vraie carte n'est débitée. L'utilisateur saisit un numéro de carte
(inscription ou `POST /api/v1/services/bank-card/add`), le backend le chiffre (AES/GCM) et en garde
les 4 derniers chiffres pour l'affichage. Au paiement, **le numéro décide de l'issue** :

| Numéro saisi | Paiement | Commande |
|---|---|---|
| uniquement des `1` (ex. `1111 1111 1111 1111`) | accepté | **PAID** |
| uniquement des `0` (ex. `0000 0000 0000 0000`) | refusé | **PAYMENT_FAILED** |
| tout autre numéro (ex. `4242 4242 4242 4242`) | refusé | **PAYMENT_FAILED** |

Le numéro doit faire 13 à 19 chiffres. Date d'expiration future, type de carte au choix.

## Comment c'est joué

`TestCardPaymentUtils` traduit le numéro en carte de test Stripe, et le paiement passe réellement
par Stripe en mode test (clé `sk_test_` de `stripe.api.key`, aucun compte personnel nécessaire) :

- que des `1` → PaymentIntent confirmé avec `pm_card_visa` → `succeeded` ;
- sinon → `pm_card_chargeDeclined` → refus `card_declined / generic_decline`.

Le webhook Stripe (`payment_intent.succeeded` / `payment_intent.payment_failed`, relayé en local par
le conteneur `stripe-cli`) fait ensuite passer la commande en PAID ou PAYMENT_FAILED, comme avant.

Vérifié de bout en bout le 2026-10-14 sur le stack de dev : `1111…` → PAID, `0000…` et `4242…` →
PAYMENT_FAILED.

## Scénarios utiles

- **Paiement réussi** : carte `1111 1111 1111 1111`, passer commande.
- **Paiement refusé puis relance réussie** : carte `0000 0000 0000 0000`, passer commande
  (PAYMENT_FAILED), supprimer la carte et enregistrer `1111…`, puis
  `POST /api/v1/services/management/order/retry-payment/{uuid}` (PAID).
- **Sans carte** : `place` et `retry-payment` répondent 404 `BANK_CARD_NOT_FOUND` (vérifié avant la
  réservation du stock pour `retry-payment`).

## Comptes de dev équipés (2026-10-14)

Carte `1111…` (paie) pour `hideyoshi.tanaseda@email.com`, `admin@email.com` et les 5 comptes
`e2e.cart.*@example.com` du front.

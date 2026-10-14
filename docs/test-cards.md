# Cartes de test Stripe

Le backend débite toujours la carte que l'utilisateur a enregistrée chez Stripe, hors session (sans
l'utilisateur, sans redirection possible). Seules les clés `sk_test_` / `pk_test_` sont utilisées :
aucun argent réel ne circule. Comportements vérifiés le 2026-10-14 contre l'API Stripe (mode test),
avec exactement les paramètres du backend.

## Quelle carte pour quel résultat

| Carte (Stripe Elements) | Jeton serveur | Enregistrement | Paiement | Commande |
|---|---|---|---|---|
| `4242 4242 4242 4242` | `pm_card_visa` | OK | accepté | **PAID** |
| `5555 5555 5555 4444` | `pm_card_mastercard` | OK | accepté | **PAID** |
| `4000 0000 0000 0341` | `pm_card_chargeCustomerFail` | OK | refusé (`card_declined` / `generic_decline`) | **PAYMENT_FAILED** |
| `4000 0027 6000 3184` | `pm_card_authenticationRequired` | OK (3-D Secure à l'enregistrement dans Elements) | refusé (`authentication_required`) | **PAYMENT_FAILED** |
| `4000 0025 0000 3155` | `pm_card_authenticationRequiredOnSetup` | 3-D Secure demandé à l'enregistrement | accepté si le 3-D Secure a été validé dans Elements, sinon `authentication_required` | PAID / PAYMENT_FAILED |
| `4000 0000 0000 9995` | - | **refusé** (`insufficient_funds` dès l'enregistrement) | - | impossible à tester |

Pour toutes les cartes : n'importe quelle date d'expiration future, n'importe quel CVC à 3 chiffres,
n'importe quel code postal.

À retenir :
- pour faire **échouer** un paiement, utiliser **0341** (la carte s'enregistre, chaque paiement est
  refusé). La 9995 ne sert pas : Stripe la refuse dès l'enregistrement ;
- pour tester « échec puis relance réussie » : enregistrer la 0341, passer commande
  (PAYMENT_FAILED), enregistrer la 4242, puis `POST /order/retry-payment/{uuid}` (PAID) ;
- les cartes créées par jeton expirent un an après leur création (expiration affichée MM/AAAA).

## Donner une carte à un compte de dev sans le front

`src/main/java/com/novatech/cybertech/utils/stripe/seedTestCards.py` fait côté serveur ce que fait
Stripe Elements : il réutilise ou crée le Customer Stripe de l'utilisateur (aucune donnée
personnelle envoyée, seulement son UUID), attache la carte de test, en fait la carte par défaut et
l'écrit dans `bankCardTable` (la carte précédente est remplacée et détachée chez Stripe).

```bash
cd src/main/java/com/novatech/cybertech/utils/stripe
python seedTestCards.py --list-cards
python seedTestCards.py --email admin@email.com --email hideyoshi.tanaseda@email.com   # Visa 4242
python seedTestCards.py --email someone@example.com --card declined                     # 0341
```

Prérequis : le stack Docker démarré (conteneur `mysqlEcommerce`), `pip install requests`. La clé
Stripe est lue dans `application.properties` (`stripe.api.key`) ou dans `STRIPE_API_KEY`.

Après un changement de compte Stripe (`stripe.api.key`), relancer le script pour chaque compte :
les Customers inconnus du nouveau compte sont recréés.

## Comptes de dev déjà équipés (2026-10-14)

Visa 4242 pour : `hideyoshi.tanaseda@email.com`, `admin@email.com` et les 5 comptes
`e2e.cart.*@example.com` du front (les seuls qui avaient une carte avant le passage à Stripe).

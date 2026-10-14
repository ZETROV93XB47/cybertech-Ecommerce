# Browser push notifications

The backend sends Web Push in addition to the existing e-mail/SMS notifications when a payment
is confirmed or an order is handed to the carrier. A browser must have granted notification
permission and registered a service worker before it can receive a push.

## VAPID configuration

Generate one VAPID key pair for the deployment (for example with `npx web-push
generate-vapid-keys`). Keep the private key in Vault or a deployment secret; never commit it.
Configure:

- `WEB_PUSH_PUBLIC_KEY`: Base64URL public key, returned by the public-key endpoint.
- `WEB_PUSH_PRIVATE_KEY`: matching Base64URL private key.
- `WEB_PUSH_SUBJECT`: contact URI such as `mailto:admin@example.com`.

The backend encrypts stored subscription endpoints and browser keys at rest using the existing
AES/GCM key (`APP_CARD_ENCRYPTION_KEY`). Keep that key stable across deployments and back it up
securely; rotating it without re-encrypting subscription rows makes those rows unusable.

Push subscription endpoints are accepted only over HTTPS from the provider host suffixes listed
in `cybertech.web-push.allowed-endpoint-hosts`. Extend this allowlist only for trusted browser
push-service domains; it prevents clients from turning the server into an arbitrary URL fetcher.

## API contract

- `GET /api/v1/services/notifications/push/public-key` (public) returns
  `{"publicKey":"..."}`.
- `POST /api/v1/services/notifications/push/subscriptions` (USER JWT) accepts the browser
  `PushSubscription.toJSON()` shape: `{"endpoint":"https://...","expirationTime":null,"keys":{"p256dh":"...","auth":"..."}}`.
  It returns `201` and a subscription `uuid`. Repeating the same endpoint for the same account
  updates that subscription; an endpoint already owned by another account is rejected.
- `DELETE /api/v1/services/notifications/push/subscriptions/{uuid}` (USER JWT) removes only a
  subscription owned by the caller.

The JWT `sub` is the only user identity used by the backend. The browser should send the
subscription response's `uuid` when unsubscribing. Service-worker click handling should open the
`url` in the push payload (`/account/orders/{orderUuid}`); adjust that path if the frontend uses a
different order-details route.

The push text is generic and excludes prices/payment details. HTTP 404/410 from a push provider
removes the expired subscription. Other delivery failures pass through the existing bounded
notification retry/audit path.

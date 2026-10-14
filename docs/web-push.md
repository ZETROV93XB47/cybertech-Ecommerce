# Browser push notifications

The backend sends Web Push in addition to the existing e-mail/SMS notifications when a payment
is confirmed or an order is handed to the carrier. A browser must have granted notification
permission and registered a service worker before it can receive a push.

Web Push is **not** a favorite communication channel: it only reaches the website, so it is
always sent, on top of the user's e-mail or SMS choice, to every browser the user subscribed.
Registration accepts only `EMAIL` or `SMS` as `favoriteCommunicationChanel` (`PUSH_NOTIFICATION`
is a 400); accounts created earlier with `PUSH_NOTIFICATION` are treated as `EMAIL`.

## Delivery

One notification is delivered (and audited) per browser subscription. A browser whose push
service fails is retried alone through the bounded notification retry/redrive path; the other
browsers are never sent the same push twice. Each delivery waits at most
`cybertech.web-push.send-timeout` (default `10s`) for the push service; a timeout is a retryable
failure. HTTP 404/410 from a push service removes the expired subscription.

## VAPID configuration

Generate one VAPID key pair for the deployment (for example with `npx web-push
generate-vapid-keys`). Keep the private key in Vault or a deployment secret; never commit it.
Configure:

- `WEB_PUSH_PUBLIC_KEY`: Base64URL public key, returned by the public-key endpoint.
- `WEB_PUSH_PRIVATE_KEY`: matching Base64URL private key.
- `WEB_PUSH_SUBJECT`: contact URI such as `mailto:admin@example.com`.

An invalid key pair is logged at startup and disables Web Push only (e-mail/SMS keep working).

Other properties (`cybertech.web-push.*`):

- `send-timeout` (default `10s`): max wait for the push service on one delivery.
- `max-subscriptions-per-user` (default `5`): registering one more browser replaces the user's
  oldest subscription.
- `order-details-url-template` (default `/account/orders/{orderUuid}`): page the service worker
  opens on click; `{orderUuid}` is replaced.

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
  updates that subscription; an endpoint already owned by another account is rejected (`409`).
  A 6th browser replaces the user's oldest subscription.
- `DELETE /api/v1/services/notifications/push/subscriptions/{uuid}` (USER JWT) removes a
  subscription owned by the caller (`204`). Another user's subscription is `403`
  `PUSH_SUBSCRIPTION_NOT_OWNED`; an unknown UUID (already removed, or replaced by a newer browser)
  is `404` `PUSH_SUBSCRIPTION_NOT_FOUND`, which the frontend can treat as already unsubscribed.

The JWT `sub` is the only user identity used by the backend. The browser should send the
subscription response's `uuid` when unsubscribing. Service-worker click handling should open the
`url` in the push payload (`order-details-url-template`, `/account/orders/{orderUuid}` by default).

The push text is generic and excludes prices/payment details.

## Dependencies

`nl.martijndwars:web-push` 5.1.2 is the latest release. Its transitive versions carry known CVEs,
so the POM pins `jose4j` 0.9.7 and `bcprov-jdk18on` 1.86, and excludes `async-http-client` and
`jcommander` (only used by the library's `PushAsyncService` and CLI, not by this backend).

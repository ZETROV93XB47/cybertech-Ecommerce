# Dev secrets for the Minikube stack — the SAME values as the local dev setup
# (application.properties + src/main/resources/docker/.env), so both environments behave alike.
# Dev-only test credentials; move them to Kubernetes Secrets before any shared deployment.
#
# Usage (Git Bash, from the repo root):
#   source src/main/resources/k8s/helm/minikube-dev-secrets.sh
# then `helmfile apply` (from src/main/resources/k8s/helm) or `skaffold dev` (from the repo root).

# Only applied when MySQL initialises an empty volume. The existing dev and Minikube volumes
# both have an empty root password; the backend connects as `rookie`.
export MYSQL_ROOT_PASSWORD="cybertech-root"

# keycloak.client.user.management.client.secret
export KEYCLOAK_CLIENT_SECRET="rPKnibr1m14c2Oit4XybU1AhhIbuZVtt"

# spring.cloud.aws.credentials.* / RUSTFS_* in docker/.env
export RUSTFS_ACCESS_KEY="cybertech-local"
export RUSTFS_SECRET_KEY="cybertech-local-secret-123456"

# Default of app.security.card-encryption-key. Changing it makes already stored cards unreadable.
export APP_CARD_ENCRYPTION_KEY="AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="

# stripe.api.key / stripe.webhook.secret. `stripe listen` derives the signing secret from the
# API key, so the in-cluster Stripe CLI produces this same whsec_.
export STRIPE_API_KEY="sk_test_VePHdqKTYQjKNInc7u56JBrQ"
export STRIPE_WEBHOOK_SECRET="whsec_465571a297ea1526fc16b254219e3370dbd478b09c37aa6ed4eaf7bb14349c8d"

export MINIKUBE_IP="$(minikube ip)"

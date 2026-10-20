# Cybertech — minikube deployment runbook

End-to-end guide for running the Cybertech backend and its support services
on a local Minikube cluster. The frontend, Vault, LocalStack, Loki, and Grafana
charts have been removed.

> Target: laptop / portfolio demo. Production hardening (TLS, managed secret storage,
> external DBs, autoscaling) is intentionally out of scope and called out
> as future work where relevant.

## 1. Prerequisites

Install the following on your host:

| Tool       | Tested version | Notes                                                    |
|------------|----------------|----------------------------------------------------------|
| `minikube` | >= 1.33        | `minikube start --addons=ingress` provisions nginx       |
| `kubectl`  | >= 1.30        | Must match minikube's k8s server version (within +/- 1)  |
| `helm`     | >= 3.14        | helmfile shells out to it                                |
| `helmfile` | >= 0.165       | Orchestrates the 3 deploy layers in `helmfile.yaml.gotmpl` |
| `docker`   | >= 24          | Used to build images directly into the minikube daemon   |

On Windows, run the commands below from **bash** (Git Bash / WSL) — PowerShell
will not handle `eval $(minikube docker-env)` correctly. The repo lives in
`C:\Users\<you>\Downloads\cybertech-dev-develop_cybertech`.

## 2. Start minikube

```bash
minikube start \
  --cpus=4 \
  --memory=8192 \
  --disk-size=40g \
  --addons=ingress
```

The full stack hits ~3 vCPU and ~6 GiB of RAM steady-state; 4 vCPU / 8 GiB
gives you headroom for `mvn test` against the cluster. Verify the ingress
controller pod is `Running`:

```bash
kubectl get pods -n ingress-nginx
```

## 3. Build images into the minikube docker daemon

`image.pullPolicy: IfNotPresent` is set on every chart, so as long as the
images exist *inside* the minikube daemon, no registry round-trip is needed.

```bash
# Point your local docker CLI at the minikube daemon for this shell only.
eval $(minikube docker-env)

# Backend (Spring Boot, multi-stage in src/main/resources/docker/dockerfile).
docker build -t cybertech-app:dev -f src/main/resources/docker/dockerfile .

# Sanity check — the backend image should appear:
docker images | grep '^cybertech-app'
```

## 4. Set required env vars

Export these values before applying the Helmfile. Get the current Minikube IP
after starting the cluster; it is used by the backend pod to reach the S3 and
OIDC ingress names.

| Variable                 | Purpose                                | Dev value (example)             |
|--------------------------|----------------------------------------|---------------------------------|
| `MYSQL_ROOT_PASSWORD`    | MySQL root + cybertech-app datasource  | `rootpw`                        |
| `MINIKUBE_IP`            | Host aliases for external ingress URLs  | `$(minikube ip)`                |
| `KEYCLOAK_CLIENT_SECRET` | Backend OAuth client; must match realm export | existing realm client secret |
| `RUSTFS_ACCESS_KEY`      | RustFS S3 access key                    | `cybertech-local`               |
| `RUSTFS_SECRET_KEY`      | RustFS S3 secret key                    | generate with `openssl rand -hex 24` |
| `APP_CARD_ENCRYPTION_KEY`| AES-256 key for stored card data        | `openssl rand -base64 32`       |
| `STRIPE_API_KEY`         | Stripe CLI test key                     | `sk_test_...`                   |
| `STRIPE_WEBHOOK_SECRET`  | Stripe webhook HMAC secret              | `whsec_...`                     |

Quick export block for bash:

```bash
export MYSQL_ROOT_PASSWORD=rootpw
export MINIKUBE_IP="$(minikube ip)"
export KEYCLOAK_CLIENT_SECRET="<copy the matching realm client secret>"
export RUSTFS_ACCESS_KEY=cybertech-local
export RUSTFS_SECRET_KEY="$(openssl rand -hex 24)"
export APP_CARD_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export STRIPE_API_KEY=sk_test_replace_me
export STRIPE_WEBHOOK_SECRET=whsec_replace_me
```

## 5. Apply the helmfile

```bash
cd src/main/resources/k8s/helm
helmfile apply
```

Helmfile dependencies order the releases as follows:

1. **mysql, mongodb, redis, elasticsearch** — data stores.
2. **keycloak, rustfs, gorse, mailpit, moderation-api, stripe** — auth and supporting services.
3. **cybertech-app** — backend.
The local deployment uses Kubernetes Secrets populated from Helmfile
environment variables. Vault, frontend, LocalStack, Loki, and Grafana charts
have been removed.
RustFS replaces LocalStack and persists the image bucket on a PVC.
Products with image URLs from an old LocalStack endpoint must have their
images uploaded again. Previously installed releases are not automatically
removed by `helmfile apply`. Inspect releases and PVCs first, then run only
the uninstall commands for releases that are actually present:

```bash
helm list -n default
kubectl get pvc -n default
helm uninstall front-app -n default
helm uninstall loki -n default
helm uninstall grafana -n default
helm uninstall localstack -n default
helm uninstall vault -n default
```

Helm uninstall may leave PVCs behind. Preserve data you need, then delete only
the specific obsolete PVCs you have identified. In the inspected cluster, only
LocalStack has a PVC; no Loki, Grafana, or Vault PVC is present. Deleting the
claim can delete its backing volume and data.

Watch progress:

```bash
kubectl get pods -w
```

## 6. Wire DNS via /etc/hosts

```bash
minikube ip
# e.g. 192.168.49.2
```

Append to `/etc/hosts` (Windows: `C:\Windows\System32\drivers\etc\hosts`):

```
192.168.49.2  api.cybertech.local keycloak.cybertech.local s3.cybertech.local
```

`api.cybertech.local` -> Spring Boot backend Service (path `/api/`).
`keycloak.cybertech.local` -> Keycloak admin console + OIDC issuer (path `/`).

## 7. Access the backend

- Backend API: <http://api.cybertech.local/api/v1/>
- Backend health and Swagger are not exposed by the `/api/` Ingress. Run
  `kubectl port-forward svc/cybertech-app 8081:8081`, then open
  <http://localhost:8081/actuator/health> or
  <http://localhost:8081/swagger-ui.html>.
- Product images: <http://s3.cybertech.local/>
- Mailpit: `kubectl port-forward svc/mailpit 8025:8025` then <http://localhost:8025/>

> **TLS is intentionally off.** For a public deployment, install
> `cert-manager`, issue a Let's Encrypt cert, and configure TLS on the
> backend, Keycloak, and RustFS ingress charts. Out of scope here.

Backend and service logs can be inspected with `kubectl logs`; no Loki/Grafana
stack or log collector is installed by this Helmfile.

## 8. Troubleshooting

### Backend pod pending or CrashLoopBackOff

```bash
kubectl logs -n default -l app=cybertech-app --tail=200
kubectl describe pod -l app=cybertech-app
```

Common causes:

- MySQL initContainer still waiting (Spring Boot will not boot until MySQL is reachable).
- `MYSQL_ROOT_PASSWORD` env var not set in the shell that ran `helmfile apply`.
- `cybertech-app:dev` not present in the minikube daemon (re-run step 3).
- `APP_CARD_ENCRYPTION_KEY` missing or not Base64-encoded 32-byte data.
- `MINIKUBE_IP` is stale after restarting/recreating Minikube; refresh it and re-apply.

### Keycloak slow to become Ready

Keycloak in `start-dev` mode boots in ~45 – 90 s on minikube: it provisions
its MySQL schema (90+ Liquibase changesets), bootstraps the master realm,
and only then starts answering on `/health/ready`. The chart's `startupProbe`
gives it a generous ~5 min budget before the kubelet declares the pod
unhealthy. Watch the boot live:

```bash
kubectl logs -f deploy/keycloak
kubectl describe pod -l app=keycloak     # probe transitions
```

If the pod is `CrashLoopBackOff`, the usual culprits are a missing
`keycloakDB` (the mysql-chart init SQL pre-creates it — re-apply mysql if
you wiped its PVC), or a `KEYCLOAK_ADMIN_PASSWORD` mismatch between the
Secret and any environment override.

### Inspect a specific pod

```bash
kubectl get pods
kubectl logs <pod-name> --previous   # crashed pod's last logs
kubectl exec -it <pod-name> -- sh    # shell inside
```

### Wipe and start over

```bash
cd src/main/resources/k8s/helm
helmfile destroy
kubectl delete pvc --all              # wipes data volumes
```

## 9. Audit notes

- Helmfile deploys RustFS and Gorse; the frontend, Vault, LocalStack, Loki, and
  Grafana charts have been removed.
- This Helmfile deploys backend services only.
- Every image is pinned to the same version as the local Compose stack. Gorse
  0.5.x needs the Redis query engine (`FT.*` commands), which is why Redis is on
  8.x rather than 7.x.
- Keycloak 26 refuses JavaScript authorization policies, so the realm export
  must not contain `"type": "js"` policies.
- Only the `default` Helmfile environment exists; there is no production values file.
- For the backend's edit/rebuild loop, use Skaffold (`skaffold.yml` at the repo
  root, see `DEPLOY_MINIKUBE.md`); it only manages the `cybertech-app` release.

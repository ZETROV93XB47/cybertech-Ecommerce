# Cybertech — minikube deployment runbook

End-to-end guide for running the Cybertech backend and its support services
with Loki/Grafana on a local Minikube cluster. The frontend chart/release has
been removed for now.

> Target: laptop / portfolio demo. Production hardening (TLS, real Vault,
> external DBs, autoscaling) is intentionally out of scope and called out
> as future work where relevant.

## 1. Prerequisites

Install the following on your host:

| Tool       | Tested version | Notes                                                    |
|------------|----------------|----------------------------------------------------------|
| `minikube` | >= 1.33        | `minikube start --addons=ingress` provisions nginx       |
| `kubectl`  | >= 1.30        | Must match minikube's k8s server version (within +/- 1)  |
| `helm`     | >= 3.14        | helmfile shells out to it                                |
| `helmfile` | >= 0.165       | Orchestrates the 4 deploy layers in `helmfile.yaml.gotmpl` |
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
4. **loki, grafana** — observability (Grafana waits on Loki).

The local deployment does not install Vault: its charts use Kubernetes Secrets
populated from environment variables. Both the Vault release and frontend
release are disabled; the Vault chart is retained, but the frontend chart has
been removed. RustFS replaces LocalStack and persists the image bucket on a
PVC; the LocalStack chart is retained but not installed.
Products with image URLs from an old LocalStack endpoint must have their
images uploaded again. Previously installed `localstack` and `vault` releases
are not automatically removed by `helmfile apply`. If a prior deployment
installed the frontend, remove that obsolete release with
`helm uninstall front-app -n default`.

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
192.168.49.2  api.cybertech.local keycloak.cybertech.local grafana.cybertech.local s3.cybertech.local
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
> backend, Keycloak, Grafana, and RustFS ingress charts. Out of scope here.

## 7b. Observability (Loki + Grafana)

The final layer deploys Loki and a Grafana UI. No Promtail/Alloy collector is
currently installed, so backend logs remain available through `kubectl logs`
and do not appear in Loki yet. Single-replica each, no HA, small PVC —
strictly portfolio-grade.
(Prometheus metrics and Tempo distributed tracing were removed: unused.)

### Reach Grafana

Either via the ingress (preferred):

```bash
minikube ip
# append to /etc/hosts (Windows: C:\Windows\System32\drivers\etc\hosts):
# <minikube-ip>  grafana.cybertech.local
```

Then open <http://grafana.cybertech.local/>.

Or via a port-forward (no /etc/hosts change required):

```bash
kubectl port-forward svc/grafana 3000:3000
# open http://localhost:3000/
```

### Login

Default: `admin` / `admin`. Stored in the `grafana-admin` Secret.
**REPLACE in real deploys via Vault** — the comment in
`charts/grafana-chart/templates/secret.yaml` flags the swap-out path
(Vault Agent injection of `/vault/secrets/grafana-admin`).

### Pre-provisioned datasource

Auto-loads on first boot via Grafana's file-based provisioning (see
`charts/grafana-chart/templates/datasources-configmap.yaml`):

| Datasource   | UID    | URL (in-cluster)    |
|--------------|--------|----------------------|
| Loki         | `loki` | `http://loki:3100`   |

### Dashboards

None ship by default (the previous metrics/tracing dashboards were removed
along with Prometheus/Tempo). The dashboards provider (`folder: Cybertech`,
`charts/grafana-chart/templates/dashboards-configmap.yaml`) is still wired
up — drop a JSON file into `charts/grafana-chart/dashboards/`, re-run
`helmfile apply`, and it picks it up automatically.

### Querying Loki for backend logs

In Grafana → Explore → Loki:

```logql
{app="cybertech-app"}
```

Common filters once the backend ships JSON logs:

```logql
{app="cybertech-app"} |= "ERROR"
{app="cybertech-app"} | json | level="ERROR"
```

The Loki datasource is ready, but the query stays empty until a log collector
is configured and deployed.

### Persistence

Loki 5Gi PVC. Wipe with the usual `kubectl delete pvc --all`.

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

### Vault

Vault is not installed or used by this local Helmfile. The chart remains in
the repository but is intentionally disabled; local secrets are passed through
Helmfile into Kubernetes Secrets.

## 9. Audit notes

- Helmfile now deploys RustFS and Gorse; the unused LocalStack and Vault
  charts remain available but are not part of the local release set.
- The frontend chart/release was removed at the project owner's request; this
  Helmfile currently deploys backend services only.
- Helm lint/render checks can validate manifests, but Minikube was stopped
  during this audit, so a live rollout and browser-based OIDC/S3 checks remain
  to be run after the cluster is started.
- The RustFS, Gorse, and AWS CLI images use `latest` to match the current
  local Compose configuration; pin image versions before treating these
  charts as production deployment artifacts.

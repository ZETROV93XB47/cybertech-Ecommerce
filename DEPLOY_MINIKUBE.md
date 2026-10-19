# Deploy Cybertech on Minikube — Step by Step

A practical walkthrough for booting the Cybertech backend stack on your local
machine: Spring Boot 4 / Java 26, its data and support services, and
Loki/Grafana. The frontend is not currently deployed by these charts.

> **Audience:** you, on your laptop. Portfolio scope. ~6 GB RAM, ~4 vCPU.
> Companion docs: [`src/main/resources/k8s/helm/README.md`](src/main/resources/k8s/helm/README.md)
> (deep helm/probe reference), [`docs/runbooks/`](docs/runbooks/) (incident playbooks).

---

## Step 0 — Install prerequisites

| Tool | Min version | Install hint |
|---|---|---|
| `minikube` | 1.33 | https://minikube.sigs.k8s.io/docs/start/ |
| `kubectl` | 1.30 | bundled with Docker Desktop, or `choco install kubernetes-cli` |
| `helm` | 3.14 | `choco install kubernetes-helm` |
| `helmfile` | 0.165 | `choco install helmfile` or grab the binary |
| `docker` | 24 | Docker Desktop |
| **JDK 26** | 26.0.1+ | https://adoptium.net/temurin/releases/?version=26 |
| `Maven` | bundled | use the `./mvnw` wrapper |

**Windows note:** run all the bash commands below from **Git Bash** or **WSL**.
PowerShell does not handle `eval $(minikube docker-env)` correctly.

Verify your tools:

```bash
minikube version
kubectl version --client
helm version
helmfile --version
docker version
java -version    # must show 26.x
```

---

## Step 1 — Start minikube

The full stack needs ~6 GiB RAM and ~3 vCPU. Give it headroom:

```bash
minikube start \
  --cpus=4 \
  --memory=8192 \
  --disk-size=40g \
  --addons=ingress
```

Wait until the ingress-controller pod is ready:

```bash
kubectl get pods -n ingress-nginx --watch
# Ctrl-C once you see ingress-nginx-controller-... 1/1 Running
```

Get the cluster IP — you'll need it for `/etc/hosts`:

```bash
minikube ip
# example output: 192.168.49.2
export MINIKUBE_IP="$(minikube ip)"
```

---

## Step 2 — Wire local DNS

The backend API, Keycloak, Grafana, and S3 ingress use
`*.cybertech.local`. Map them to the minikube IP.

**Linux / macOS:** edit `/etc/hosts` (sudo required):

```
192.168.49.2  api.cybertech.local keycloak.cybertech.local grafana.cybertech.local s3.cybertech.local
```

**Windows:** edit `C:\Windows\System32\drivers\etc\hosts` as Administrator with
the same line.

Verify DNS resolution (NOT reachability — minikube isn't up yet, and even when
it is, the Docker driver typically doesn't forward ICMP to the cluster):

```bash
# Linux / macOS / Git Bash — DNS lookup only:
getent hosts api.cybertech.local || nslookup api.cybertech.local

# Windows PowerShell:
Resolve-DnsName api.cybertech.local -CacheOnly
```

The output should show your minikube IP. **Ping will time out even when this
is correct** — that's normal. The real reachability check is `curl` against
the backend after Step 5 brings the cluster up.

---

## Step 3 — Build the application images

Point your local Docker CLI at minikube's Docker daemon so the images land
inside the cluster directly (no registry round-trip needed):

```bash
eval $(minikube docker-env)
```

> If you close this shell, you'll need to re-run that line. Run `minikube docker-env -u` to switch back to your host Docker.

Build the backend image (uses the existing Dockerfile; takes 2–4 min on first build):

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"   # adjust path
./mvnw clean package -DskipTests
docker build -t cybertech-app:dev -f src/main/resources/docker/dockerfile .
```

Only the backend application image is built by this guide:

```bash
minikube image ls | grep cybertech-app
# expect docker.io/library/cybertech-app:dev
```

---

## Step 4 — Set required env vars

`helmfile.yaml.gotmpl` requires these values before `helmfile apply`:

```bash
export MYSQL_ROOT_PASSWORD="cybertech-root"
export KEYCLOAK_CLIENT_SECRET="<match cybertech-user-management-client in the Helm realm export>"
export RUSTFS_ACCESS_KEY="cybertech-local"
export RUSTFS_SECRET_KEY="$(openssl rand -hex 24)"
export APP_CARD_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export STRIPE_API_KEY="sk_test_PLACEHOLDER"
export STRIPE_WEBHOOK_SECRET="whsec_PLACEHOLDER"
```

`MINIKUBE_IP` must also be set as shown in Step 1. The backend encryption key
must remain stable to decrypt existing stored cards. Use alphanumeric MySQL
and RustFS credentials for this local setup.

> **Real Stripe keys:** if you want webhooks to actually work, drop your real
> `sk_test_…` and the matching webhook signing secret here. The Wave 3 fix in
> `04ea74b` makes the webhook handler tolerant of API-version skew, so any
> recent Stripe account works.

---

## Step 5 — Deploy the stack

The `helmfile.yaml.gotmpl` orders the stack through dependencies. One command
brings it up:

```bash
cd src/main/resources/k8s/helm
helmfile apply
```

Expected wall time: **8–12 min** on first run (Keycloak alone can take
~60–90 s to start). Subsequent applies are faster.

What `helmfile apply` actually does, in order:

| Layer | Charts | Purpose |
|---|---|---|
| 1 | `mysql`, `mongodb`, `redis`, `elasticsearch` | Data stores |
| 2 | `keycloak`, `rustfs`, `gorse`, `mailpit`, `moderation-api`, `stripe` | Auth + side services |
| 3 | `cybertech-app` | Spring Boot backend |
| 4 | `loki`, `grafana` | Observability UI (log collector not installed) |

RustFS persists objects on a PVC and initializes the `cybertech-products`
bucket, public image-read policy, and browser CORS. Its S3 endpoint is
`http://s3.cybertech.local`. The LocalStack chart remains in the repository
but is no longer installed. Vault is intentionally disabled and its chart is
retained for future use; Minikube secrets are Kubernetes Secrets populated
through Helmfile environment variables.
Products with image URLs from an old LocalStack endpoint must have their
images uploaded again. If you previously installed the old `localstack` or
`vault` or `front-app` releases, Helmfile does not automatically remove them.
Check `helm list -n default`; remove the obsolete `front-app` release with
`helm uninstall front-app -n default`. Only remove an old `vault` release if
you are sure it contains no data you need.

Watch the rollout:

```bash
kubectl get pods -w
# Ctrl-C once every pod is Running and Ready
```

If a pod crashloops, jump to **Step 9 — Troubleshooting**.

---

## Step 6 — Verify the deploy

### Backend health

```bash
kubectl port-forward svc/cybertech-app 8081:8081
# In another terminal:
curl http://localhost:8081/actuator/health
# expect: {"status":"UP", ...}
```

The Ingress intentionally routes only `/api/`; Actuator and Swagger are not
published through the API hostname. With the port-forward running, Swagger UI
is at `http://localhost:8081/swagger-ui.html`.

### Keycloak admin console

Open `http://keycloak.cybertech.local` → **Administration Console** → log in
with `admin / admin`.

### OpenAPI / Postman

The OpenAPI YAML is committed at [`docs/openapi/openapi.yaml`](docs/openapi/openapi.yaml).
Import [`docs/postman/Cybertech-API.postman_collection.json`](docs/postman/) into
Postman; the pre-request script auto-fetches a Keycloak token.

### Place an order end-to-end

```bash
# Get a JWT (replace user/pass with one you registered first)
TOKEN=$(curl -s -X POST http://keycloak.cybertech.local/realms/cybertech/protocol/openid-connect/token \
  -d "client_id=cybertech-user-management-client" \
  -d "client_secret=$KEYCLOAK_CLIENT_SECRET" \
  -d "grant_type=password" \
  -d "username=test@example.com" -d "password=test" | jq -r .access_token)

# Hit a protected endpoint
curl -H "Authorization: Bearer $TOKEN" http://api.cybertech.local/api/v1/services/cart/list
```

---

## Step 7 — Access observability

Grafana and Loki are deployed, but no Promtail/Alloy log collector is part of
the current Helmfile. Backend logs remain available through `kubectl logs`;
Grafana/Loki will not show them until a collector is added.

```
URL:      http://grafana.cybertech.local
Login:    admin / admin
```

Pre-provisioned datasource:
- Loki → `http://loki:3100`

The Loki datasource is preconfigured, but currently has no log ingestion
pipeline. Metrics dashboards and distributed tracing (Prometheus, Tempo) were
removed because they were unused.

---

## Step 8 — Optional: Run Gatling load tests

The simulations live in `src/test/java/com/novatech/cybertech/gatling/` under
the `loadtest` Maven profile.

```bash
# Acquire a JWT (same TOKEN dance as Step 6)
export GATLING_TOKEN=$TOKEN

./mvnw gatling:test -Ploadtest \
  -Dgatling.simulationClass=com.novatech.cybertech.gatling.BrowseToOrderSimulation \
  -Dgatling.baseUrl=http://api.cybertech.local \
  -Dauth.token=$GATLING_TOKEN
```

Reports land in `target/gatling/`. Three simulations available — see
[`src/test/java/com/novatech/cybertech/gatling/README.md`](src/test/java/com/novatech/cybertech/gatling/README.md).

---

## Step 9 — Troubleshooting

### Pod stuck `Pending`
```bash
kubectl describe pod <name>
```
Most common causes: PVC pending (storage class issue), image not in cluster
(re-run `minikube image ls`), insufficient cluster resources (bump
`--cpus`/`--memory`).

### Pod crashloops
```bash
kubectl logs <name> --previous
```
- Backend can't connect to Keycloak → wait longer, Keycloak takes ~60 s.
- Backend OOM → increase its memory in `cybertech-app-chart/values.yaml`.
- Elasticsearch JVM crash → bump `--memory` on minikube; the ES 9 chart reserves a 1 GiB heap.

### Ingress 404 / 502
```bash
kubectl get ingress
kubectl get svc
```
Verify `kubectl get svc <name>` returns the expected port. Confirm `/etc/hosts`
points at the right minikube IP (`minikube ip` may change on `minikube delete`).

### `mvn clean install` fails on JaCoCo gate
The gate is meaningful only with IT coverage. Either:
```bash
./mvnw clean install                          # fast, gate skipped
./mvnw clean install -Pintegration-test       # full, gate enforced
```

### Out of disk space
```bash
minikube ssh -- 'df -h /var/lib/docker'
docker image prune -a                # in minikube docker-env
helmfile destroy                     # nuke and redeploy
```

### Need a clean restart
```bash
helmfile destroy
minikube delete
# then start over from Step 1
```

---

## Step 10 — Teardown

When you're done:

```bash
# Stop the stack but keep cluster state
helmfile destroy

# Stop the cluster (preserves data)
minikube stop

# Or nuke everything
minikube delete --all
```

---

## URL cheat sheet

| Service | URL | Credentials |
|---|---|---|
| Backend API | `http://api.cybertech.local` | Bearer JWT from Keycloak |
| Backend health | Port-forward `svc/cybertech-app`; then `http://localhost:8081/actuator/health` | none |
| Keycloak | `http://keycloak.cybertech.local` | admin / admin |
| Grafana | `http://grafana.cybertech.local` | admin / admin |
| Mailpit (in cluster) | `http://mailpit:8025` (port-forward) | none |
| Product images (S3) | `http://s3.cybertech.local` | public read |

For port-forwarding Mailpit so you can read captured emails in a browser:

```bash
kubectl port-forward svc/mailpit 8025:8025
# then open http://localhost:8025
```

---

## What's NOT covered (intentional portfolio scope)

| Area | Status | Where to look when ready |
|---|---|---|
| TLS on ingress | HTTP only | `cert-manager` + Let's Encrypt; bump `ingress.tls.enabled` per chart |
| Vault integration | Disabled; local values use Kubernetes Secrets | enable and configure deliberately when needed |
| Real SMTP | Mailpit | swap `application-prod.properties` `spring.mail.*` |
| Pen test | Skipped | run OWASP ZAP first, then a vendor |
| GDPR right-to-erasure | Documented, not implemented | `docs/runbooks/gdpr-data-handling.md` has the spec |
| Backups | Skipped | for portfolio data loss is acceptable |
| HA / multi-replica | Single replica each | bump `replicaCount` in chart values + add HPA |

---

## Where to go next

- **Something broke?** → [`docs/runbooks/`](docs/runbooks/) for incident playbooks
- **Deep helm reference** → [`src/main/resources/k8s/helm/README.md`](src/main/resources/k8s/helm/README.md)
- **API contract** → [`docs/openapi/openapi.yaml`](docs/openapi/openapi.yaml) + [`docs/postman/`](docs/postman/)
- **Project history** → [`progress.md`](progress.md) (waves 1–6 bug fixes), [`LAUNCH_PROGRESS.md`](LAUNCH_PROGRESS.md) (waves 7–9 launch prep)
- **Next session menu** → bottom of [`LAUNCH_PROGRESS.md`](LAUNCH_PROGRESS.md)

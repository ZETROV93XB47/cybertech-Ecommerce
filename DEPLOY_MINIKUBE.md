# Deploy Cybertech on Minikube — Step by Step

A practical walkthrough for booting the full Cybertech stack on your local
machine. Backend (Spring Boot 4 / Java 26), frontend (Next.js 16), 11 supporting
services, 4 observability services — all running in one minikube cluster.

> **Audience:** you, on your laptop. Portfolio scope. ~6 GB RAM, ~4 vCPU.
> Companion docs: [`src/main/resources/k8s/helm/README.md`](src/main/resources/k8s/helm/README.md)
> (deep helm/probe reference), [`jenkins/README.md`](jenkins/README.md)
> (CI pipeline), [`docs/runbooks/`](docs/runbooks/) (incident playbooks).

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
| `Node.js` | 22 LTS | optional — only if rebuilding the frontend by hand |

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
  --addons=ingress \
  --insecure-registry="host.docker.internal:5000,10.0.0.0/24"
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
```

---

## Step 2 — Wire local DNS

The frontend, backend, Keycloak, and Grafana all live on `*.cybertech.local`.
Map them to the minikube IP.

**Linux / macOS:** edit `/etc/hosts` (sudo required):

```
192.168.49.2  cybertech.local api.cybertech.local keycloak.cybertech.local grafana.cybertech.local
```

**Windows:** edit `C:\Windows\System32\drivers\etc\hosts` as Administrator with
the same line.

Verify DNS resolution (NOT reachability — minikube isn't up yet, and even when
it is, the Docker driver typically doesn't forward ICMP to the cluster):

```bash
# Linux / macOS / Git Bash — DNS lookup only:
getent hosts cybertech.local || nslookup cybertech.local

# Windows PowerShell:
Resolve-DnsName cybertech.local -CacheOnly
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

Build the frontend image (Wave 7B added the Dockerfile + standalone Next.js
output; ~3 min on first build):

```bash
docker build -t cybertech-front:dev -f front/app/Dockerfile front/app
```

Confirm both images are visible to minikube:

```bash
minikube image ls | grep cybertech
# expect:
# docker.io/library/cybertech-app:dev
# docker.io/library/cybertech-front:dev
```

---

## Step 4 — Set required env vars

`helmfile.yaml.gotmpl` references several secrets via `requiredEnv`. Export them
before running `helmfile apply`. For portfolio scope, dummy values are fine:

```bash
export MYSQL_ROOT_PASSWORD="cybertech-root"
export KEYCLOAK_CLIENT_SECRET="cybertech-portfolio-secret"
export VAULT_TOKEN="cybertech-dev-token"
export AWS_SECRET_ACCESS_KEY="test"      # localstack accepts any value
export STRIPE_API_KEY="sk_test_PLACEHOLDER"
export STRIPE_WEBHOOK_SECRET="whsec_PLACEHOLDER"
```

> **Real Stripe keys:** if you want webhooks to actually work, drop your real
> `sk_test_…` and the matching webhook signing secret here. The Wave 3 fix in
> `04ea74b` makes the webhook handler tolerant of API-version skew, so any
> recent Stripe account works.

---

## Step 5 — Deploy the stack

The `helmfile.yaml.gotmpl` orchestrates 6 layers. One command brings them all up:

```bash
cd src/main/resources/k8s/helm
helmfile apply
```

Expected wall time: **5–8 min** on first run (Keycloak alone takes ~60 s to
start). Subsequent applies are fast.

What `helmfile apply` actually does, in order:

| Layer | Charts | Purpose |
|---|---|---|
| 1 | `vault` | Secrets store |
| 2 | `mysql`, `mongodb`, `redis`, `elasticsearch` | Data stores |
| 3 | `keycloak`, `localstack`, `mailpit`, `moderation-api`, `stripe` | Auth + side services |
| 4 | `cybertech-app` | Spring Boot backend |
| 5 | `front-app` | Next.js frontend |
| 6 | `prometheus`, `loki`, `tempo`, `grafana` | Observability |

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
curl -k https://api.cybertech.local/actuator/health
# expect: {"status":"UP","components":{...}}
```

### Frontend

Open `https://cybertech.local` in a browser. The catalog page should render.

### Keycloak admin console

Open `https://keycloak.cybertech.local` → **Administration Console** → log in
with `admin / admin`.

### OpenAPI / Postman

The OpenAPI YAML is committed at [`docs/openapi/openapi.yaml`](docs/openapi/openapi.yaml).
Import [`docs/postman/Cybertech-API.postman_collection.json`](docs/postman/) into
Postman; the pre-request script auto-fetches a Keycloak token.

### Place an order end-to-end

```bash
# Get a JWT (replace user/pass with one you registered first)
TOKEN=$(curl -s -X POST https://keycloak.cybertech.local/realms/cybertech/protocol/openid-connect/token \
  -d "client_id=cybertech-user-management-client" \
  -d "client_secret=$KEYCLOAK_CLIENT_SECRET" \
  -d "grant_type=password" \
  -d "username=test@example.com" -d "password=test" | jq -r .access_token)

# Hit a protected endpoint
curl -H "Authorization: Bearer $TOKEN" https://api.cybertech.local/api/v1/services/cart/list
```

---

## Step 7 — Access observability

Grafana is the single pane of glass for metrics, logs, and traces.

```
URL:      https://grafana.cybertech.local
Login:    admin / admin
```

Pre-provisioned dashboards:
- **cybertech-overview** — orders/sec, payment success rate, JVM heap, request rate, error rate
- **cybertech-jvm** — heap, GC time, threads, classes loaded
- **cybertech-stripe-webhooks** — webhook 4xx/5xx, dedup ledger, signature failures
- **cybertech-traces-overview** — Tempo RED dashboard for the backend service

Pre-provisioned datasources:
- Prometheus → `http://prometheus:9090`
- Loki → `http://loki:3100`
- Tempo → `http://tempo:3200`

Quick log query in Loki: `{app="cybertech-app"}` shows backend logs in JSON
format with `traceId`, `spanId`, `userKeycloakId`, `orderUuid` MDC keys.

To find a trace in Tempo: copy the `traceId` from any log line, paste it into
Tempo's search box.

---

## Step 8 — Optional: Jenkins CI/CD pipeline

Spin up a local Jenkins controller + Docker registry as a separate compose
stack (does NOT run in minikube — uses your host Docker):

```bash
docker compose -f docker-compose.jenkins.yml up -d --build
```

Wait ~2 min for first boot, then:

```
Jenkins UI:          http://localhost:8090   (admin / admin via JCasC)
Docker registry:     localhost:5000
Registry UI browser: http://localhost:8091
```

Edit `jenkins/casc.yaml` to point the `cybertech-pipeline` job at your repo
URL (placeholder is `REPLACE-ME`). Restart Jenkins:

```bash
docker compose -f docker-compose.jenkins.yml restart jenkins
```

Trigger the pipeline manually in the UI or wait for the SCM poller (every 5
min). Full setup details: [`jenkins/README.md`](jenkins/README.md).

---

## Step 9 — Optional: Run Gatling load tests

The simulations live in `src/test/java/com/novatech/cybertech/gatling/` under
the `loadtest` Maven profile.

```bash
# Acquire a JWT (same TOKEN dance as Step 6)
export GATLING_TOKEN=$TOKEN

./mvnw gatling:test -Ploadtest \
  -Dgatling.simulationClass=com.novatech.cybertech.gatling.BrowseToOrderSimulation \
  -Dgatling.baseUrl=https://api.cybertech.local \
  -Dauth.token=$GATLING_TOKEN
```

Reports land in `target/gatling/`. Three simulations available — see
[`src/test/java/com/novatech/cybertech/gatling/README.md`](src/test/java/com/novatech/cybertech/gatling/README.md).

---

## Step 10 — Troubleshooting

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
- Elasticsearch JVM crash → bump `--memory` on minikube; ES 8 needs ~1 GiB heap minimum.

### Ingress 404 / 502
```bash
kubectl get ingress
kubectl get svc
```
Verify `kubectl get svc <name>` returns the expected port. Confirm `/etc/hosts`
points at the right minikube IP (`minikube ip` may change on `minikube delete`).

### Stripe webhook not firing
The `cybertech.security.stripe-ip-allowlist.enabled` flag is **true** in
`application-prod.properties`. For local Stripe CLI testing, override it:
```bash
kubectl set env deploy/cybertech-app CYBERTECH_SECURITY_STRIPE_IP_ALLOWLIST_ENABLED=false
```

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

## Step 11 — Teardown

When you're done:

```bash
# Stop the stack but keep cluster state
helmfile destroy

# Stop the cluster (preserves data)
minikube stop

# Or nuke everything
minikube delete --all
```

If you also brought up Jenkins:

```bash
docker compose -f docker-compose.jenkins.yml down -v
```

---

## URL cheat sheet

| Service | URL | Credentials |
|---|---|---|
| Frontend | `https://cybertech.local` | n/a (or your registered user) |
| Backend API | `https://api.cybertech.local` | Bearer JWT from Keycloak |
| Backend health | `https://api.cybertech.local/actuator/health` | none |
| Keycloak | `https://keycloak.cybertech.local` | admin / admin |
| Grafana | `https://grafana.cybertech.local` | admin / admin |
| Mailpit (in cluster) | `http://mailpit:8025` (port-forward) | none |
| Jenkins | `http://localhost:8090` | admin / admin |
| Docker Registry UI | `http://localhost:8091` | none |

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
| Real Vault secrets | Static values via env | switch to ExternalSecret / SecretProviderClass |
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

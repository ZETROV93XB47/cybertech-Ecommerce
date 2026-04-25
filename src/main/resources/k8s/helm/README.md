# Cybertech — minikube deployment runbook

End-to-end guide for running the full Cybertech stack (backend + Next.js
frontend + 9 supporting services) on a local minikube cluster.

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
| `helmfile` | >= 0.165       | Orchestrates the 5 deploy layers in `helmfile.yaml`      |
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
docker build -t cybertech-app:1.2 -f src/main/resources/docker/dockerfile .

# Frontend (Next.js standalone, see front/app/Dockerfile).
docker build -t cybertech-front:dev front/app

# Sanity check — both should appear:
docker images | grep -E '^(cybertech-app|cybertech-front)\b'
```

> If `docker build` for the frontend fails because `npm ci` cannot reach the
> registry from inside the minikube VM, re-run the build outside
> `minikube docker-env` and `minikube image load cybertech-front:dev` to push
> the resulting tarball into the cluster.

## 4. Set required env vars

`helmfile.yaml` uses `requiredEnv` for genuinely sensitive values and
`env ... | default ...` for things that have safe dev defaults. The
required set:

| Variable                 | Purpose                                | Dev value (example)             |
|--------------------------|----------------------------------------|---------------------------------|
| `MYSQL_ROOT_PASSWORD`    | MySQL root + cybertech-app datasource  | `rootpw`                        |
| `KEYCLOAK_CLIENT_SECRET` | OAuth2 client secret used by backend   | `dev-kc-secret`                 |
| `VAULT_TOKEN`            | Vault root token (dev mode)            | `root`                          |
| `AWS_SECRET_ACCESS_KEY`  | localstack S3 secret key               | `test`                          |
| `STRIPE_API_KEY`         | Stripe CLI test key                    | `sk_test_...`                   |
| `STRIPE_WEBHOOK_SECRET`  | Stripe webhook HMAC secret             | `whsec_...`                     |

Optional overrides (defaults apply if unset):

| Variable                       | Default                           |
|--------------------------------|-----------------------------------|
| `FRONT_IMAGE_REPOSITORY`       | `cybertech-front`                 |
| `FRONT_IMAGE_TAG`              | `dev`                             |
| `FRONT_AUTH_SECRET`            | `dev-auth-secret-change-me`       |
| `FRONT_KEYCLOAK_CLIENT_SECRET` | `dev-keycloak-front-secret-...`   |

Quick export block for bash:

```bash
export MYSQL_ROOT_PASSWORD=rootpw
export KEYCLOAK_CLIENT_SECRET=dev-kc-secret
export VAULT_TOKEN=root
export AWS_SECRET_ACCESS_KEY=test
export STRIPE_API_KEY=sk_test_replace_me
export STRIPE_WEBHOOK_SECRET=whsec_replace_me
```

## 5. Apply the helmfile

```bash
cd src/main/resources/k8s/helm
helmfile apply
```

This rolls out 6 layers in dependency order:

1. **vault** — secrets store (dev mode, root token).
2. **mysql, mongodb, redis, elasticsearch** — stateful infra (PVCs).
3. **keycloak, localstack, mailpit, moderation-api, stripe** — services that consume layer 2.
4. **cybertech-app** — backend (waits on every layer-2/3 release).
5. **front-app** — Next.js frontend (waits on `cybertech-app`).
6. **prometheus, loki, tempo, grafana** — observability stack (Grafana waits on the three backends).

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
192.168.49.2  cybertech.local api.cybertech.local keycloak.cybertech.local
```

`cybertech.local` -> Next.js frontend Service.
`api.cybertech.local` -> Spring Boot backend Service (path `/api/`).
`keycloak.cybertech.local` -> Keycloak admin console + OIDC issuer (path `/`).

## 7. Open the app

- Frontend: <http://cybertech.local/>
- Backend health: <http://api.cybertech.local/api/v1/actuator/health>
- Swagger UI: <http://api.cybertech.local/api/v1/swagger-ui.html>
- Mailpit: `kubectl port-forward svc/mailpit 8025:8025` then <http://localhost:8025/>

> **TLS is intentionally off.** For a public deployment, install
> `cert-manager`, issue a Let's Encrypt cert (or self-signed for purely
> local play), set `ingress.tls.enabled=true` on both `cybertech-app-chart`
> and `front-app-chart`, point `ingress.tls.secretName` at the cert, and
> add a `redirect-http-to-https` annotation. Out of scope for this runbook.

## 7b. Observability (Prometheus + Loki + Tempo + Grafana)

Layer 6 ships a self-hosted observability stack — metrics, logs, traces and a
Grafana UI on top. None of it is required to run the app; the backend just
pushes to it when it is up. Single-replica each, no HA, small PVCs — strictly
portfolio-grade.

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

### Pre-provisioned datasources

All three backends auto-load on first boot via Grafana's file-based
provisioning (see `charts/grafana-chart/templates/datasources-configmap.yaml`):

| Datasource   | UID          | URL (in-cluster)        |
|--------------|--------------|-------------------------|
| Prometheus   | `prometheus` | `http://prometheus:9090` |
| Loki         | `loki`       | `http://loki:3100`       |
| Tempo        | `tempo`      | `http://tempo:3200`      |

Tempo is wired to Loki + Prometheus (`tracesToLogsV2`, `tracesToMetrics`,
`serviceMap`, `nodeGraph`), so a span page links straight back into the
matching logs and span-derived metrics. Loki has a `derivedFields` rule
that turns any `traceId` JSON field into a clickable Tempo link.

### Dashboards

Four skeleton dashboards ship as JSON in
`charts/grafana-chart/dashboards/` and are mounted into Grafana via a
ConfigMap. The dashboards provider (`folder: Cybertech`) picks them up
automatically. Edit the JSON, re-run `helmfile apply`, refresh.

| Dashboard UID                 | Shows                                                                |
|-------------------------------|----------------------------------------------------------------------|
| `cybertech-overview`          | Orders/sec, payment success rate, request rate, 5xx rate, JVM heap   |
| `cybertech-jvm`               | Heap used vs max, GC time, threads (live/daemon), classes loaded     |
| `cybertech-stripe-webhooks`   | Webhook 4xx/5xx, dedup ledger size, signature failures, events by type |
| `cybertech-traces-overview`   | RED metrics from Tempo span-metrics + recent traces for `cybertech-app` |

### Querying Loki for backend logs

In Grafana → Explore → Loki:

```logql
{app="cybertech-app"}
```

Common filters once the backend ships JSON logs:

```logql
{app="cybertech-app"} |= "ERROR"
{app="cybertech-app"} | json | level="ERROR"
{app="cybertech-app"} | json | traceId="<your-trace-id>"
```

(The parallel agent wires Spring Boot to logstash-logback-encoder + a log
shipper. This chart only stands up the Loki backend.)

### Finding a trace in Tempo

Cybertech-app responses include the trace ID as a header (e.g.
`X-Trace-Id: 4bf92f3577b34da6a3ce929d0e0e4736`). To inspect that trace:

1. Grafana → Explore → Tempo datasource.
2. Switch query type to **TraceQL** (or **Search**).
3. Paste the trace ID into the Trace ID field, or run:

   ```
   { trace:id="4bf92f3577b34da6a3ce929d0e0e4736" }
   ```

4. Click any span → "Logs for this span" jumps into Loki via the
   provisioned `tracesToLogsV2` link. "Metrics" jumps into the matching
   span-metrics in Prometheus.

### Alert rules — what is and is not wired

`charts/prometheus-chart/templates/rules-configmap.yaml` ships skeleton
rules: 5xx rate > 1% over 5m, JVM heap > 85% over 10m, payment success
rate < 95% over 15m, Stripe webhook failures > 5/min. They are evaluated
by Prometheus and visible in its UI but **no Alertmanager is deployed** —
pages, Slack messages and PagerDuty are intentionally out of scope.
For a real deploy: add an `alertmanager-chart`, point Prometheus'
`alerting.alertmanagers` at it, and wire the receivers there. Grafana
OnCall is also intentionally not deployed (no real paging on minikube).

### Persistence

Prometheus 1Gi, Loki 5Gi, Tempo 5Gi PVCs. Wipe with the usual
`kubectl delete pvc --all`.

## 8. Troubleshooting

### Backend pod pending or CrashLoopBackOff

```bash
kubectl logs -n default -l app=cybertech-app --tail=200
kubectl describe pod -l app=cybertech-app
```

Common causes:

- MySQL initContainer still waiting (Spring Boot will not boot until MySQL is reachable).
- `MYSQL_ROOT_PASSWORD` env var not set in the shell that ran `helmfile apply`.
- `cybertech-app:1.2` not present in the minikube daemon (re-run step 3).

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

### Frontend pod stuck `ImagePullBackOff`

You forgot `eval $(minikube docker-env)` before building. Fix:

```bash
eval $(minikube docker-env)
docker build -t cybertech-front:dev front/app
kubectl rollout restart deploy/front-app
```

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

### Vault wiring (real deploy)

In production, the `front-app-chart` and `cybertech-app-chart` should
**not** ship plaintext secrets in `values.yaml`. The intended pattern:

1. Vault is seeded with the Keycloak client secret, AUTH_SECRET, etc.
2. Each chart adds a `serviceAccountName` and a Vault Agent injector
   annotation that mounts the secret as a file under `/vault/secrets/`.
3. The container reads the file via Spring Cloud Vault (backend) or via
   `dotenv`-style loading at startup (frontend).
4. The `secrets.*` and `STRIPE_*` env vars in `values.yaml` are removed
   in favour of the file-mounted versions.

The placeholder `Secret` objects in this repo exist only so the local
minikube deploy works without standing up Vault first.

## 9. Audit notes (existing charts)

Captured during the Wave 7B audit; track in a follow-up if you care about
production-readiness:

- **keycloak-chart** rewritten in Wave 8 (chart `0.2.0`, app `26.0.4`):
  full deployment with ports, startup/liveness/readiness probes against
  `:9000/health/*`, resource requests + limits, runAsNonRoot security
  context, dedicated Secret, Service (8080 + 9000) and Ingress on
  `keycloak.cybertech.local`. Runs in `start-dev` mode for portfolio.
- **moderation-api-chart** has no probes and no resources block.
- **vault-chart** ships its PVC commented out — runs ephemeral. Fine for
  dev (root token is hardcoded), unacceptable for prod.
- **mysql-chart** has resource requests but no `livenessProbe` /
  `readinessProbe` — pods may be considered Ready before MySQL is actually
  accepting connections, which is why `keycloak-chart` and
  `cybertech-app-chart` use TCP-wait initContainers.
- **cybertech-app-chart** Service had no `name` on its port; harmless in
  k8s 1.28+ but flagged by `helm lint`.

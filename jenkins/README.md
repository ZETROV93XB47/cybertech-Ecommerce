# Cybertech CI/CD — Jenkins on Docker

Self-hosted CI/CD that runs entirely on your local Docker daemon. No cloud
services required. The pipeline builds the backend (Spring Boot 4 / Java 26),
runs unit + integration tests, builds the frontend (Next.js 16), packages
both as container images, pushes them to a local `registry:2`, helm-lints
the charts, and (on `master`) deploys to your local minikube cluster
through `helmfile apply`.

```
┌──────────────────────────────────────────────────────────────┐
│  docker-compose.jenkins.yml                                  │
│                                                              │
│   ┌────────────┐   ┌────────────┐   ┌──────────────────┐     │
│   │  jenkins   │──▶│  registry  │◀──│  registry-ui     │     │
│   │  :8090     │   │  :5000     │   │  :8091           │     │
│   └─────┬──────┘   └─────┬──────┘   └──────────────────┘     │
│         │                │                                   │
│         │ docker.sock    │                                   │
└─────────┼────────────────┼───────────────────────────────────┘
          ▼                ▼
   host docker daemon   minikube  (--insecure-registry)
```

## 1. Prerequisites

- Docker Desktop (Windows/Mac) or Docker Engine 24+ on Linux.
- Free host ports: **8090** (Jenkins), **5000** (registry), **8091** (registry UI), **50000** (Jenkins JNLP).
- Minikube installed and runnable on your machine (only required for the deploy stage).
- This repository checked out at `dev/develop`.

## 2. Bring up the CI infrastructure

From the repository root:

```bash
docker compose -f docker-compose.jenkins.yml up -d --build
```

The first run will build the custom Jenkins image (downloads JDK 26, installs
kubectl/helm/helmfile, preinstalls plugins). Expect ~3-5 minutes.

Verify:

```bash
docker compose -f docker-compose.jenkins.yml ps
docker compose -f docker-compose.jenkins.yml logs -f jenkins
```

Open Jenkins: <http://localhost:8090>
Open the registry UI: <http://localhost:8091>
The registry's HTTP API: <http://localhost:5000/v2/_catalog>

### Sign-in

The Configuration-as-Code (`jenkins/casc.yaml`) provisions a single user:

| user  | password | role                 |
|-------|----------|----------------------|
| admin | admin    | logged-in users: all |

**Change the password from the UI immediately after first login.**

If JCasC fails to load (rare — usually a yaml typo), the setup wizard is still
disabled by `jenkins/init.groovy.d/disable-setup-wizard.groovy`, but you'll
need the bootstrap admin secret instead:

```bash
docker exec cybertech-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

## 3. Plugins

The image preinstalls the following plugins via `jenkins-plugin-cli`
(see `jenkins/plugins.txt`):

- `git`, `workflow-aggregator`, `pipeline-stage-view`, `pipeline-utility-steps`
- `docker-workflow`, `docker-plugin`
- `nodejs`, `config-file-provider`
- `configuration-as-code`, `job-dsl`
- `junit`, `htmlpublisher`
- `blueocean`, `ansicolor`, `timestamper`, `build-timeout`
- `credentials`, `credentials-binding`, `plain-credentials`, `ssh-credentials`
- `github`, `github-branch-source`

If you add plugins, edit `jenkins/plugins.txt` and rebuild:
`docker compose -f docker-compose.jenkins.yml up -d --build jenkins`.

## 4. Tools

JCasC declares three tools the `Jenkinsfile` references:

| Tool name | What it points at                                   |
|-----------|-----------------------------------------------------|
| `jdk26`   | `/opt/java/jdk-26` (Eclipse Temurin 26.0.1+9, baked in) |
| `maven`   | Auto-installer, version `3.9.9`                     |
| `nodejs`  | Auto-installer, version `22.11.0` LTS               |

Inspect / override under **Manage Jenkins → Tools**.

## 5. The pipeline job

JCasC also creates a `cybertech-pipeline` job pointing at this repository's
`Jenkinsfile`. **Edit the SCM URL** in `jenkins/casc.yaml` (search for
`REPLACE-ME`) before the first run, or override it from the job UI:

- **Pipeline → Definition:** *Pipeline script from SCM*
- **SCM:** Git
- **Repository URL:** your local clone path (`/workspace/cybertech` if you
  bind-mount it) **or** your GitHub URL once pushed.
- **Branch:** `*/dev/develop`
- **Script Path:** `Jenkinsfile`
- **Trigger:** *Poll SCM* `H/5 * * * *`

Click **Build Now**. A successful run looks like:

```
✓ Checkout
✓ Backend Lint+Compile
✓ Backend Unit Tests
✓ Backend Integration Tests
✓ Backend JaCoCo Gate          (HTML report archived)
✓ Frontend Build
✓ Backend Image                (cybertech-app:<sha>, :latest)
✓ Frontend Image               (cybertech-front:<sha>, :latest)
✓ Push Images                  → localhost:5000
✓ Helm Lint                    (cybertech-app-chart, front-app-chart)
⏸ Deploy to Staging (manual)  ← input gate (master only)
```

Browse the registry UI at <http://localhost:8091> to confirm the tags.

## 6. Wiring minikube to the local registry

`registry:2` serves plain HTTP. Docker (and minikube's docker daemon) refuse
HTTP registries by default, so you must opt in:

| OS / driver         | Flag                                                                |
|---------------------|---------------------------------------------------------------------|
| Mac / Windows       | `minikube start --insecure-registry="host.docker.internal:5000"`    |
| Linux (default br)  | `minikube start --insecure-registry="172.17.0.1:5000"`              |
| Existing cluster    | `minikube delete` then re-create with the flag (the flag is sticky on cluster create only) |

Then update the helm `image.repository` values to match the route minikube
uses (e.g. `host.docker.internal:5000/cybertech-app` on Mac/Win,
`172.17.0.1:5000/cybertech-app` on Linux). The `Jenkinsfile` pushes to
`localhost:5000` (which resolves on the host) — minikube needs the
docker-network-routable form.

A cleaner alternative on Linux is `minikube addons enable registry` and
push to `localhost:5000` after `kubectl port-forward -n kube-system svc/registry 5000:80`.

## 7. Troubleshooting

| Symptom                                                                 | Fix                                                                                                                                                            |
|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Cannot connect to the Docker daemon` inside Jenkins                     | The bind-mount `/var/run/docker.sock:/var/run/docker.sock` is missing or the Jenkins user can't read it. On Linux: `sudo chmod 666 /var/run/docker.sock` (dev only) or add the jenkins container's gid to the `docker` group. The compose file already sets `user: root` to side-step this on Linux dev boxes. |
| Pipeline reports `JAVA_HOME=/opt/java/openjdk` (= JDK 21, not 26)       | The `tools { jdk 'jdk26' }` block didn't take effect. Check **Manage Jenkins → Tools → JDK installations** lists `jdk26` with home `/opt/java/jdk-26`. JCasC sets this, so reapply the config (`Manage Jenkins → Configuration as Code → Reload existing configuration`).                                       |
| `docker push … http: server gave HTTP response to HTTPS client`          | Either (a) your *host* docker daemon needs `localhost:5000` in `insecure-registries` (Docker Desktop → Settings → Docker Engine → add `"insecure-registries": ["localhost:5000"]`), or (b) minikube wasn't started with `--insecure-registry`. Restart docker / minikube after editing.                          |
| `npm ci` fails with `prebuild` eslint missing                            | The repo's `front/app/package.json` lists eslint as a devDependency, so `npm ci` installs it. If you see this anyway, delete `front/app/node_modules` and `front/app/package-lock.json` is *not* the right move — check that the Jenkins `nodejs` tool installed npm into PATH (`which npm` in a pipeline `sh`). |
| `helmfile apply` fails with `helm-diff: plugin not found`              | The Dockerfile installs `helm-diff` via `helm plugin install` for the `jenkins` user. If you wiped `/var/jenkins_home`, the plugin's gone too — rebuild the image (`up -d --build jenkins`).                                                                                                                     |
| Frontend image build fails with `pnpm not found` / wrong package manager | The Next.js Dockerfile detects the lockfile. We ship `package-lock.json`, so npm should be picked. If you switch to pnpm/yarn, update `front/app/Dockerfile` accordingly (out-of-scope for this CI work).                                                                                                       |
| Setup wizard appears anyway                                              | Both layers (`JAVA_OPTS=-Djenkins.install.runSetupWizard=false` and the groovy init script) failed. Inspect the controller logs for a stack trace. Quick unblock: `docker exec cybertech-jenkins touch /var/jenkins_home/jenkins.install.InstallUtil.lastExecVersion`.                                          |
| Pipeline can't find the repo                                             | Update the `url(...)` in `jenkins/casc.yaml` (search `REPLACE-ME`) and reload JCasC. Or edit the job from the UI.                                                                                                                                                                                                |

## 8. Tearing it down

```bash
# Stop and remove containers (keeps volumes / build cache)
docker compose -f docker-compose.jenkins.yml down

# Stop and remove EVERYTHING including registry blobs and Jenkins config
docker compose -f docker-compose.jenkins.yml down -v
rm -rf jenkins-data/ registry-data/   # only if you used host bind-mounts
```

## 9. What's intentionally NOT done here

- **GitHub webhook integration.** The job uses SCM polling
  (`H/5 * * * *`). Once you push the repo to GitHub, swap the trigger to
  `githubPush()` in `casc.yaml` and configure a webhook in the repo settings
  (`Settings → Webhooks → Payload URL = http://<your-tunnel>/github-webhook/`).
- **Jenkins agent isolation.** Builds run on the controller (`agent any`).
  For multi-tenant or heavier workloads, add a Docker Cloud / Kubernetes Cloud
  configuration and switch to ephemeral agents.
- **Vault / secrets binding.** The `dockerHubRegistry` credential is a stub.
  Bind real secrets via `withCredentials` in the `Jenkinsfile` once needed.
- **Production deploy.** The `Deploy to Staging (manual)` stage is only enabled
  on `master`/`main` and is gated by an input. There is no production stage —
  add one alongside it when you're ready.

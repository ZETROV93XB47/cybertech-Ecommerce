# syntax=docker/dockerfile:1.6
#
# Cybertech Jenkins controller image.
#
# Base: jenkins/jenkins:lts-jdk21 (Jenkins LTS bundles its own JDK 21; we keep that
# for the controller process itself and add JDK 26 as a *tool* the pipeline switches to).
#
# Adds:
#   - Eclipse Temurin JDK 26.0.1 GA at /opt/java/jdk-26  (declared as Jenkins tool 'jdk26')
#   - docker-cli (talks to host's /var/run/docker.sock — Docker-out-of-Docker)
#   - kubectl 1.31.0
#   - helm 3.15.4
#   - helmfile 0.169.1
#   - Configuration-as-Code (JCasC) yaml + a groovy init script (defence-in-depth
#     so the setup wizard stays disabled even if JCasC parsing fails).
#
# All version pins live in the ARG block below — bump them in one place.

FROM jenkins/jenkins:lts-jdk21

# ---- pin tool versions -------------------------------------------------------
ARG JDK26_VERSION=26.0.1+9
# URL-encoded form of "26.0.1+9" -> "26.0.1%2B9" (used in the Adoptium tarball name)
ARG JDK26_VERSION_URL=26.0.1%2B9
# Tarball name fragment used by the Adoptium release artefacts.
ARG JDK26_TARBALL_VERSION=26.0.1_9
ARG KUBECTL_VERSION=v1.31.0
ARG HELM_VERSION=v3.15.4
ARG HELMFILE_VERSION=0.169.1

USER root

# ---- baseline tooling --------------------------------------------------------
# curl + ca-certificates are already present in the base image; we add gnupg
# (needed for docker repo signing key) and a few small utilities.
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      gnupg \
      lsb-release \
      git \
      jq \
      unzip \
      bash-completion; \
    rm -rf /var/lib/apt/lists/*

# ---- JDK 26 (Eclipse Temurin) ------------------------------------------------
# Adoptium publishes GA tarballs at:
#   https://github.com/adoptium/temurin26-binaries/releases/download/jdk-<ver>/
#     OpenJDK26U-jdk_x64_linux_hotspot_<ver_underscore>.tar.gz
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
      amd64) jdk_arch=x64 ;; \
      arm64) jdk_arch=aarch64 ;; \
      *) echo "Unsupported arch for JDK 26 install: $arch" >&2; exit 1 ;; \
    esac; \
    url="https://github.com/adoptium/temurin26-binaries/releases/download/jdk-${JDK26_VERSION_URL}/OpenJDK26U-jdk_${jdk_arch}_linux_hotspot_${JDK26_TARBALL_VERSION}.tar.gz"; \
    echo "Fetching $url"; \
    mkdir -p /opt/java; \
    curl -fsSL "$url" -o /tmp/jdk26.tar.gz; \
    tar -xzf /tmp/jdk26.tar.gz -C /opt/java; \
    mv "/opt/java/jdk-${JDK26_VERSION}" /opt/java/jdk-26; \
    rm /tmp/jdk26.tar.gz; \
    /opt/java/jdk-26/bin/java -version

ENV JDK26_HOME=/opt/java/jdk-26

# ---- docker CLI --------------------------------------------------------------
# We do NOT install the daemon; the controller talks to the host's docker.sock.
RUN set -eux; \
    install -m 0755 -d /etc/apt/keyrings; \
    curl -fsSL https://download.docker.com/linux/debian/gpg \
      | gpg --dearmor -o /etc/apt/keyrings/docker.gpg; \
    chmod a+r /etc/apt/keyrings/docker.gpg; \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
      https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
      > /etc/apt/sources.list.d/docker.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin docker-compose-plugin; \
    rm -rf /var/lib/apt/lists/*; \
    docker --version

# ---- kubectl -----------------------------------------------------------------
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
      amd64) k_arch=amd64 ;; \
      arm64) k_arch=arm64 ;; \
      *) echo "Unsupported arch for kubectl: $arch" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${k_arch}/kubectl" \
      -o /usr/local/bin/kubectl; \
    chmod 0755 /usr/local/bin/kubectl; \
    kubectl version --client=true

# ---- helm --------------------------------------------------------------------
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
      amd64) h_arch=amd64 ;; \
      arm64) h_arch=arm64 ;; \
      *) echo "Unsupported arch for helm: $arch" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://get.helm.sh/helm-${HELM_VERSION}-linux-${h_arch}.tar.gz" \
      -o /tmp/helm.tar.gz; \
    tar -xzf /tmp/helm.tar.gz -C /tmp; \
    mv "/tmp/linux-${h_arch}/helm" /usr/local/bin/helm; \
    chmod 0755 /usr/local/bin/helm; \
    rm -rf /tmp/helm.tar.gz "/tmp/linux-${h_arch}"; \
    helm version

# ---- helmfile ----------------------------------------------------------------
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
      amd64) hf_arch=amd64 ;; \
      arm64) hf_arch=arm64 ;; \
      *) echo "Unsupported arch for helmfile: $arch" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://github.com/helmfile/helmfile/releases/download/v${HELMFILE_VERSION}/helmfile_${HELMFILE_VERSION}_linux_${hf_arch}.tar.gz" \
      -o /tmp/helmfile.tar.gz; \
    mkdir -p /tmp/helmfile-extract; \
    tar -xzf /tmp/helmfile.tar.gz -C /tmp/helmfile-extract; \
    mv /tmp/helmfile-extract/helmfile /usr/local/bin/helmfile; \
    chmod 0755 /usr/local/bin/helmfile; \
    rm -rf /tmp/helmfile.tar.gz /tmp/helmfile-extract; \
    helmfile version

# ---- helm plugins required by helmfile --------------------------------------
# helmfile relies on `helm-diff` for `helmfile diff/apply`.
USER jenkins
RUN set -eux; \
    helm plugin install https://github.com/databus23/helm-diff --version v3.9.11 || true
USER root

# ---- JCasC + init.groovy.d ---------------------------------------------------
# Configuration-as-Code definition; mounted via CASC_JENKINS_CONFIG (set in the compose file).
RUN mkdir -p /var/jenkins_home/casc_configs /usr/share/jenkins/ref/init.groovy.d
COPY casc.yaml /var/jenkins_home/casc_configs/jenkins.yaml
COPY init.groovy.d/disable-setup-wizard.groovy /usr/share/jenkins/ref/init.groovy.d/disable-setup-wizard.groovy

# ---- preinstall plugins via the Jenkins helper ------------------------------
# We use install-plugins.sh / jenkins-plugin-cli (whichever the base image ships).
# Plugin IDs match what the README documents.
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Ensure jenkins owns its home tree (we ran as root for the install steps).
RUN chown -R jenkins:jenkins /var/jenkins_home

USER jenkins

# Document where JDK 26 is so the JCasC tool config can find it.
ENV PATH=/opt/java/jdk-26/bin:$PATH

// Cybertech CI/CD pipeline.
//
// Runs on the Jenkins controller image built from jenkins/Dockerfile (JDK 26 +
// maven + nodejs + docker-cli + kubectl + helm + helmfile preinstalled or
// declared as Jenkins tools).
//
// Stages:
//   1. Checkout
//   2. Backend Lint+Compile
//   3. Backend Unit Tests
//   4. Backend Integration Tests   (mvn verify -Pintegration-test — Wave 7A PRE-5 fix)
//   5. Backend JaCoCo Gate         (gate enforced inside the maven build itself; this
//                                   stage just archives the report)
//   6. Frontend Build              (npm ci && npm run build — eslint runs as `prebuild`)
//   7. Backend Image               (docker build + tag :sha + :latest)
//   8. Frontend Image
//   9. Push Images                 (with retry — local registry can be flaky on first start)
//  10. Helm Lint
//  11. Deploy to Staging (manual)  (master branch only; helmfile apply against minikube)
//
// Notes:
//   * Uses `./mvnw` (the wrapper) rather than the tool-config'd `mvn` for parity
//     with the rest of the codebase (CLAUDE.md: "Maven (wrapper `./mvnw`)").
//   * IMAGE_TAG = first 8 chars of GIT_COMMIT. The :latest tag is also pushed so
//     the helm charts can default to it.
//   * Minikube must be started with --insecure-registry to pull from
//     localhost:5000 — see jenkins/README.md.

pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 90, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    tools {
        jdk    'jdk26'
        maven  'maven'
        nodejs 'nodejs'
    }

    environment {
        REGISTRY        = 'localhost:5000'
        BACKEND_IMAGE   = "${REGISTRY}/cybertech-app"
        FRONTEND_IMAGE  = "${REGISTRY}/cybertech-front"
        // Defensive: GIT_COMMIT is null on initial seed runs without an SCM checkout.
        IMAGE_TAG       = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : 'dev'}"
        // Skaffold/CI marker so Spring picks the right profile if you wire it later.
        CI              = 'true'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : 'dev'
                    echo "Building images tagged: ${env.IMAGE_TAG} (and :latest)"
                }
            }
        }

        stage('Backend Lint+Compile') {
            steps {
                sh './mvnw -B -ntp clean compile'
            }
        }

        stage('Backend Unit Tests') {
            steps {
                // -DskipITs ensures Failsafe ITs do NOT run here; surefire (UTs) only.
                sh './mvnw -B -ntp test -DskipITs'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Backend Integration Tests') {
            steps {
                // PRE-5 (Wave 7A): ITs only run when the integration-test profile is active.
                sh './mvnw -B -ntp verify -Pintegration-test -DskipUTs=false'
            }
            post {
                always {
                    junit testResults: 'target/failsafe-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Backend JaCoCo Gate') {
            // The 80% line+branch gate is enforced by `jacoco:check` inside the maven
            // build (haltOnFailure=true). If we got here, it passed — archive the report.
            steps {
                archiveArtifacts artifacts: 'target/site/jacoco/**', fingerprint: true, allowEmptyArchive: true
                publishHTML(target: [
                    reportDir:            'target/site/jacoco',
                    reportFiles:          'index.html',
                    reportName:           'JaCoCo Coverage',
                    keepAll:              true,
                    alwaysLinkToLastBuild: true,
                    allowMissing:         true
                ])
            }
        }

        stage('Frontend Build') {
            steps {
                dir('front/app') {
                    // npm ci honours package-lock.json. The `prebuild` script runs eslint.
                    sh 'npm ci --no-audit --no-fund'
                    sh 'npm run build'
                }
            }
        }

        stage('Backend Image') {
            steps {
                sh '''
                    set -eu
                    docker build \
                        -t ${BACKEND_IMAGE}:${IMAGE_TAG} \
                        -f src/main/resources/docker/dockerfile \
                        .
                    docker tag ${BACKEND_IMAGE}:${IMAGE_TAG} ${BACKEND_IMAGE}:latest
                '''
            }
        }

        stage('Frontend Image') {
            steps {
                sh '''
                    set -eu
                    docker build \
                        -t ${FRONTEND_IMAGE}:${IMAGE_TAG} \
                        -f front/app/Dockerfile \
                        front/app
                    docker tag ${FRONTEND_IMAGE}:${IMAGE_TAG} ${FRONTEND_IMAGE}:latest
                '''
            }
        }

        stage('Push Images') {
            steps {
                // The local registry can take a moment to settle on first compose-up;
                // retry pushes up to 3 times before failing the stage.
                retry(3) {
                    sh '''
                        set -eu
                        docker push ${BACKEND_IMAGE}:${IMAGE_TAG}
                        docker push ${BACKEND_IMAGE}:latest
                        docker push ${FRONTEND_IMAGE}:${IMAGE_TAG}
                        docker push ${FRONTEND_IMAGE}:latest
                    '''
                }
            }
        }

        stage('Helm Lint') {
            steps {
                sh '''
                    set -eu
                    cd src/main/resources/k8s/helm
                    helm lint charts/cybertech-app-chart
                    helm lint charts/front-app-chart
                '''
            }
        }

        stage('Deploy to Staging (manual)') {
            when {
                anyOf {
                    branch 'master'
                    branch 'main'
                }
            }
            steps {
                input message: 'Deploy to minikube?', ok: 'Apply'
                sh '''
                    set -eu
                    cd src/main/resources/k8s/helm
                    export BACKEND_IMAGE_TAG=${IMAGE_TAG}
                    export FRONTEND_IMAGE_TAG=${IMAGE_TAG}
                    helmfile apply
                '''
            }
        }
    }

    post {
        always {
            // Catch-all in case a stage forgot to publish its own reports.
            junit testResults: 'target/surefire-reports/*.xml,target/failsafe-reports/*.xml',
                  allowEmptyResults: true
        }
        success {
            echo "Pipeline OK. Images pushed: ${env.BACKEND_IMAGE}:${env.IMAGE_TAG}, ${env.FRONTEND_IMAGE}:${env.IMAGE_TAG}"
        }
        failure {
            echo 'Pipeline failed — check stage logs above.'
        }
        cleanup {
            // Best-effort cleanup of dangling images on the Jenkins host; ignore errors.
            sh 'docker image prune -f --filter "until=24h" || true'
        }
    }
}

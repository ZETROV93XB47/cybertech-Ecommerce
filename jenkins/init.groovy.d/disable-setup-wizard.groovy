/*
 * Defence-in-depth: even if JCasC fails to load, this groovy init script
 * disables the setup wizard the moment Jenkins boots. Runs once on startup
 * (Jenkins copies anything under /usr/share/jenkins/ref/init.groovy.d/ into
 * $JENKINS_HOME/init.groovy.d/ on first launch).
 *
 * Source pattern: https://www.jenkins.io/doc/book/installing/docker/#preinstalling-plugins
 */
import jenkins.model.Jenkins
import hudson.util.VersionNumber

def instance = Jenkins.getInstance()
if (instance != null) {
    instance.setInstallState(jenkins.install.InstallState.INITIAL_SETUP_COMPLETED)
    instance.save()
}

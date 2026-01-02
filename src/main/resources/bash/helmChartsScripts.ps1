#MySQL
helm install mysql bitnami/mysql --set auth.rootPassword="" --set auth.allowEmptyPassword=true --set auth.database=cybertechDB --set auth.username=rookie --set auth.password=pwd --set primary.persistence.enabled=true --set primary.persistence.size=5Gi

#Redis
helm install redis bitnami/redis --set architecture=standalone --set auth.enabled=false --set master.persistence.enabled=true --set master.persistence.size=1Gi

#ElasticSearch
helm install elasticsearch bitnami/elasticsearch --set master.replicas=1 --set data.replicas=0 --set coordinating.replicas=0 --set ingest.replicas=0 --set master.persistence.size=5Gi --set security.enabled=false --set master.heapSize=512m

helm install elasticsearch bitnami/elasticsearch --set global.storageClass=standard --set master.replicaCount=1 --set data.replicaCount=0 --set ingest.replicaCount=0 --set coordinating.replicaCount=0 --set master.persistence.size=5Gi --set security.enabled=false --set master.heapSize=512m

#Keycloak
helm install keycloak bitnami/keycloak --set postgresql.enabled=false --set externalDatabase.host=mysql --set externalDatabase.user=rookie --set externalDatabase.password=pwd --set externalDatabase.database=keycloakDB --set auth.adminUser=admin --set auth.adminPassword=admin --set proxy=edge --set production=false


helm install mysql bitnami/mysql --set image.repository=mysql --set image.tag=8.0 --set auth.database=cybertechDB --set auth.rootPassword=root



# 1. Nettoyage complet
helm uninstall mysql
kubectl delete pvc --all

# 2. Installation avec le nouveau chemin "Legacy"
helm install mysql bitnami/mysql --set image.registry=docker.io --set image.repository=bitnamilegacy/mysql --set image.tag=8.0.36 --set auth.database=cybertechDB --set auth.username=rookie --set auth.password=pwd --set auth.rootPassword=rootpwd --set primary.persistence.enabled=true --set primary.persistence.size=5Gi --set primary.containerSecurityContext.runAsUser=0
minikube start --driver=docker
minikube docker-env | Invoke-Expression
kubectl get nodes
kubectl get pods -A

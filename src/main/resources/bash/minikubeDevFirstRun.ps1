minikube stop
minikube delete
minikube start --cpus=4 --memory=8GB --driver=docker
minikube status
minikube docker-env | Invoke-Expression



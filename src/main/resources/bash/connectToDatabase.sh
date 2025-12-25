#!/bin/bash

# Charger les variables depuis le .env
if [ -f .env ]; then
  export $(grep -v '^#' ../docker/.env | xargs)
else
  echo ".env file not found!"
  exit 1
fi

# Connexion à MySQL dans le conteneur Docker
docker exec -it mysql_ecommerce mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"

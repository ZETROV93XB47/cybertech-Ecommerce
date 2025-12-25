# Charger les variables depuis le .env
$envFile = "../docker/.env"
if (!(Test-Path $envFile)) {
    Write-Error ".env file not found!, check that you added an environment varavle file '.env' with the required args to start the container"
    exit 1
}

# Lire les variables
Get-Content $envFile | ForEach-Object {
    if ($_ -notmatch '^#' -and $_ -match '^(.*?)=(.*)$') {
        $key = $matches[1]
        $value = $matches[2]
        [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

# Connexion à MySQL dans le conteneur Docker
docker exec -it "$env:MYSQL_CONTAINER_NAME" mysql -u "root" "$env:MYSQL_DATABASE"

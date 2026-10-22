# ================================
# Export complet Keycloak (realm + users + clients + rôles)
# ================================

Write-Host "📦 Arrêt du stack Docker..."
docker compose down

# Dossier d’export
$exportDir = "$PSScriptRoot/keycloak-export"

if (!(Test-Path $exportDir)) {
    New-Item -ItemType Directory -Path $exportDir | Out-Null
}

Write-Host "📁 Dossier d'export : $exportDir"

Write-Host "🚀 Lancement du conteneur Keycloak en mode export..."

docker run --rm `
  --network ecommerce-net `
  -v "$exportDir:/opt/keycloak/export" `
  -e KC_DB=mysql `
  -e KC_DB_URL="jdbc:mysql://mysql:3306/keycloakDB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" `
  -e KC_DB_USERNAME=rookie `
  -e KC_DB_PASSWORD=pwd `
  quay.io/keycloak/keycloak:24.0.3 `
  export --dir=/opt/keycloak/export --users=realm_file

Write-Host "✅ Export terminé ! Les fichiers sont dans : $exportDir"

Write-Host "🔄 Redémarrage du stack..."
docker compose up -d

Write-Host "🎉 Export Keycloak terminé avec succès."

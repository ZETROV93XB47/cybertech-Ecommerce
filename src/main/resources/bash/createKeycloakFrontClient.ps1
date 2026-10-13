# ================================
# Crée le client Keycloak du front Next.js (cybertech-front-client) dans un realm DÉJÀ importé.
#
# Pourquoi : docker compose lance Keycloak avec --import-realm (stratégie IGNORE_EXISTING). Le
# client est déclaré dans keycloak/import/cybertech-realm.json, mais un realm déjà présent dans le
# volume keycloakDB n'est jamais ré-importé : sur une base existante, il faut créer le client à la
# main ou via ce script. Idempotent : ne fait rien si le client existe déjà.
#
# Usage : ./createKeycloakFrontClient.ps1 [-KeycloakUrl http://localhost:8080]
# ================================

param(
    [string]$KeycloakUrl = "http://localhost:8080",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$Realm = "cybertech"
)

$ErrorActionPreference = "Stop"
$clientId = "cybertech-front-client"

Write-Host "🔑 Récupération d'un token admin sur $KeycloakUrl..."
$token = (Invoke-RestMethod -Method Post `
    -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{ grant_type = "password"; client_id = "admin-cli"; username = $AdminUser; password = $AdminPassword }).access_token
$headers = @{ Authorization = "Bearer $token" }

$existing = Invoke-RestMethod -Headers $headers -Uri "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$clientId"
if ($existing.Count -gt 0) {
    Write-Host "✅ Le client $clientId existe déjà dans le realm $Realm — rien à faire."
    exit 0
}

# Même définition que dans keycloak/import/cybertech-realm.json — à garder synchronisées.
$client = @{
    clientId                  = $clientId
    name                      = $clientId
    description               = "Next.js front (Auth.js v5 BFF) - Authorization Code + PKCE, confidential"
    rootUrl                   = "http://localhost:3000"
    baseUrl                   = "http://localhost:3000"
    enabled                   = $true
    clientAuthenticatorType   = "client-secret"
    secret                    = "dev-keycloak-front-secret-change-me"
    redirectUris              = @("http://localhost:3000/api/auth/callback/keycloak", "https://cybertech.local/api/auth/callback/keycloak")
    webOrigins                = @("http://localhost:3000", "https://cybertech.local")
    standardFlowEnabled       = $true
    implicitFlowEnabled       = $false
    directAccessGrantsEnabled = $false
    serviceAccountsEnabled    = $false
    publicClient              = $false
    frontchannelLogout        = $true
    protocol                  = "openid-connect"
    attributes                = @{
        "pkce.code.challenge.method" = "S256"
        "post.logout.redirect.uris"  = "http://localhost:3000/*##https://cybertech.local/*"
        "use.refresh.tokens"         = "true"
    }
    defaultClientScopes       = @("web-origins", "acr", "profile", "roles", "email")
    optionalClientScopes      = @("address", "phone", "offline_access", "microprofile-jwt")
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Headers $headers -ContentType "application/json" `
    -Uri "$KeycloakUrl/admin/realms/$Realm/clients" -Body $client | Out-Null

Write-Host "🎉 Client $clientId créé (secret dev : dev-keycloak-front-secret-change-me)."

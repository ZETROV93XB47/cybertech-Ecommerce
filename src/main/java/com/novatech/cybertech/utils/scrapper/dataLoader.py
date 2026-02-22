# -*- coding: utf-8 -*-
import os
import re
import json
import requests
import boto3
import logging

# -----------------------------
# CONFIG
# -----------------------------
KEYCLOAK_URL = "http://localhost:8080/realms/cybertech/protocol/openid-connect/token"
CLIENT_ID = "cybertech-user-management-client"
CLIENT_SECRET = "rPKnibr1m14c2Oit4XybU1AhhIbuZVtt"

USERNAME = "melvin.west"
PASSWORD = "admin"

API_URL = "http://localhost:8081/api/v1/services/admin/management/product/create-with-image"

BUCKET = "cybertech-products"
S3_PREFIX = "products/images/"
PRODUCTS_JSON = "products.json"

# LocalStack S3 client
s3 = boto3.client(
    "s3",
    endpoint_url="http://localhost:4566",
    aws_access_key_id="test",
    aws_secret_access_key="test",
    region_name="us-east-1"
)

# -----------------------------
# LOGGING
# -----------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s"
)
log = logging.getLogger()


# -----------------------------
# UTILS
# -----------------------------
def sanitize_filename(name: str) -> str:
    name = name.strip().upper()
    name = re.sub(r"[^A-Z0-9]+", "_", name)
    return name[:60]


def extract_brand(product_name):
    name_upper = product_name.upper()

    for brand in [
        "ASUS", "HP", "LENOVO", "DELL", "AORUS", "MSI", "ACER",
        "TOSHIBA", "ALIENWARE", "MICROSOFT", "ALTYK", "GIGABYTE", "SAMSUNG"
    ]:
        if brand in name_upper:
            return brand

    return "ASUS"  # fallback safe


def map_attributes_ldlc_to_computer(raw, product_brand):
    """
    Convertit les attributs LDLC en ComputerAttributes conformes.
    """
    def extract_int(value):
        if not value:
            return 0
        digits = re.findall(r"\d+", value)
        return int(digits[0]) if digits else 0


    def extract_disk_size(value):
        """
        Exemples :
        - "SSD 1 To" -> 1024
        - "SSD 512 Go" -> 512
        - "SSD 2 To" -> 2048
        """
        if not value:
            return 0

        match = re.search(r"(\d+)\s*(To|Go)", value, re.IGNORECASE)
        if not match:
            return 0

        size = int(match.group(1))
        unit = match.group(2).lower()

        if unit == "to":   # Terabytes
            return size * 1024
        return size        # Gigabytes


    def map_cpu(raw):
        return raw.get("processeur") or raw.get("cpu") or "Unknown CPU"

    def map_gpu(raw):
        return raw.get("chipset graphique") or raw.get("gpu") or "Unknown GPU"

    def map_ram(raw):
        return extract_int(
            raw.get("taille de la mémoire")
            or raw.get("mémoire")
            or raw.get("ram")
        )

    def map_os(raw):
        return raw.get("système d'exploitation") or raw.get("os") or "Unknown OS"


    def map_connectivity(raw):
        """
        Concatène proprement :
        - norme(s) réseau sans-fil
        - technologie bluetooth
        - connecteur(s) disponible(s)
        - sans-fil
        - technologie intel vpro
        """

        fields = [
            raw.get("norme(s) réseau sans-fil"),
            raw.get("technologie bluetooth"),
            raw.get("connecteur(s) disponible(s)"),
            raw.get("sans-fil"),
            raw.get("technologie intel vpro")
        ]

        # Filtrer None et chaînes vides
        fields = [f for f in fields if f and f.strip()]

        return ", ".join(fields) if fields else "Unknown connectivity"



    def map_display_type(raw):
        return (
                raw.get("type d'écran")
                or raw.get("type de dalle")
                or raw.get("écran")
                or "Unknown display"
        )


    def map_memory(raw):
        return extract_disk_size(
            raw.get("configuration disque(s)")
            or raw.get("disque fourni")
            or raw.get("stockage")
            or raw.get("memory")
        )

    def map_brand(product_brand):
        return product_brand


    return {
        "cpu": map_cpu(raw),
        "gpu": map_gpu(raw),
        "ram": map_ram(raw),
        "os": map_os(raw),
        "connectivity": map_connectivity(raw),
        "displayType": map_display_type(raw),
        "memory": map_memory(raw),
        "brand": map_brand(product_brand)
    }


# -----------------------------
# 1. Token Keycloak
# -----------------------------
def get_access_token():
    log.info("🔐 Récupération du token Keycloak...")

    data = {
        "grant_type": "password",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "username": USERNAME,
        "password": PASSWORD
    }

    r = requests.post(KEYCLOAK_URL, data=data)

    if r.status_code != 200:
        log.error(f"❌ Erreur Keycloak : {r.status_code} - {r.text}")
        raise Exception("Impossible d'obtenir un token")

    token = r.json()["access_token"]
    log.info("✅ Token récupéré avec succès")
    return token


# -----------------------------
# 2. Bucket S3
# -----------------------------
def ensure_bucket():
    try:
        s3.create_bucket(Bucket=BUCKET)
        log.info(f"🪣 Bucket créé : {BUCKET}")
    except Exception:
        log.info(f"🪣 Bucket déjà existant : {BUCKET}")


# -----------------------------
# 3. Upload S3
# -----------------------------
def upload_image_to_s3(local_path, filename):
    key = S3_PREFIX + filename
    log.info(f"⬆️ Upload S3 : {key}")

    s3.upload_file(local_path, BUCKET, key)

    return f"http://localhost:4566/{BUCKET}/{key}"


# -----------------------------
# 4. Envoi API Spring
# -----------------------------
def send_product_to_api(product, token):
    headers = {
        "Authorization": f"Bearer {token}"
    }

    local_image_path = product["images"][0]
    s3_photo = product["s3Images"][0]
    brand = extract_brand(product["name"])

    # Mapping attributes LDLC → ComputerAttributes
    mapped_attributes = map_attributes_ldlc_to_computer(product["attributes"], brand)

    product_dto = {
        "name": product["name"],
        "price": product["price"],
        "brand": brand,
        "category": "COMPUTER",
        "photo": s3_photo,
        "stock": 10000,
        "description": product["description"],
        "attributes": mapped_attributes
    }

    log.info(f"📄 DTO envoyé : {json.dumps(product_dto)[:300]}...")

    with open(local_image_path, "rb") as img:
        files = {
            "image": (os.path.basename(local_image_path), img, "image/jpeg"),
            "product": ("product", json.dumps(product_dto), "application/json")
        }

        log.info(f"📦 Envoi produit : {product['name']}")
        r = requests.post(API_URL, headers=headers, files=files)


        if r.status_code not in (200, 201):
            log.error(f"❌ Erreur API : {r.status_code} - {r.text}")
        else:
            log.info(f"✅ Produit créé : {product['name']}")


# -----------------------------
# MAIN
# -----------------------------
def main():
    log.info("🚀 Démarrage ingestion complète")

    token = get_access_token()
    ensure_bucket()

    with open(PRODUCTS_JSON, "r", encoding="utf-8") as f:
        products = json.load(f)

    for p in products:
        log.info("\n==============================")
        log.info(f"🖥️  Traitement : {p['name']}")
        log.info("==============================")

        new_image_urls = []
        base_slug = sanitize_filename(p["name"])

        for i, img_path in enumerate(p["images"]):
            filename = f"{base_slug}_{i}.jpg"
            s3_url = upload_image_to_s3(img_path, filename)
            new_image_urls.append(s3_url)

        p["s3Images"] = new_image_urls

        send_product_to_api(p, token)

    log.info("🎉 Ingestion terminée avec succès")


if __name__ == "__main__":
    main()
# -*- coding: utf-8 -*-
"""
Envoie les produits de products.json (produit par laptopScrapper.py) à l'API admin du backend.

Usage :
    python dataLoader.py                          # toutes les catégories du JSON
    python dataLoader.py --categories MONITOR     # seulement certaines

Chaque produit porte sa "category" (COMPUTER, DESKTOP, MONITOR, SMARTPHONE, SMARTWATCH ; COMPUTER par défaut pour un
ancien products.json) : les attributs LDLC sont convertis vers le JSON Schema de cette catégorie,
et la marque est lue dans la ligne "Marque" de la fiche technique LDLC.
"""
import argparse
import os
import re
import json
from collections import Counter

import requests
import logging

# -----------------------------
# CONFIG
# -----------------------------
KEYCLOAK_URL = "http://localhost:8080/realms/cybertech/protocol/openid-connect/token"
CLIENT_ID = "cybertech-user-management-client"
CLIENT_SECRET = "rPKnibr1m14c2Oit4XybU1AhhIbuZVtt"

USERNAME = "hideyoshi.tanaseda"
PASSWORD = "password"

BACKEND_URL = "http://localhost:8081"
API_URL = BACKEND_URL + "/api/v1/services/admin/management/product/create-with-image"
CATEGORY_SCHEMA_URL = BACKEND_URL + "/api/v1/services/product-category-schemas/{}"
# La liste des marques acceptées vient de l'OpenAPI du backend (enum Brand), pas d'une copie ici.
OPENAPI_URL = BACKEND_URL + "/v3/api-docs"

PRODUCTS_JSON = "products.json"
DEFAULT_CATEGORY = "COMPUTER"

# -----------------------------
# LOGGING
# -----------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s"
)
log = logging.getLogger()


class SkipProduct(Exception):
    """Produit qu'on n'envoie pas (marque inconnue, attribut obligatoire absent...)."""


# -----------------------------
# UTILS
# -----------------------------
def normalize_label(label):
    """'Système d’exploitation ' -> "système d'exploitation" (apostrophe typographique, espaces)."""
    return re.sub(r"\s+", " ", label.replace("’", "'")).strip().lower()


def spec(raw, *labels):
    """Première valeur non vide parmi les libellés LDLC donnés (insensible à la casse et aux apostrophes)."""
    normalized = {normalize_label(k): v for k, v in raw.items()}
    for label in labels:
        value = normalized.get(normalize_label(label))
        if value and value.strip():
            return value.strip()
    return None


def extract_int(value):
    if not value:
        return 0
    digits = re.findall(r"\d+", value)
    return int(digits[0]) if digits else 0


def extract_float(value):
    """'7.6 pouces' -> 7.6 ; '6,1"' -> 6.1"""
    if not value:
        return None
    match = re.search(r"\d+(?:[.,]\d+)?", value)
    return float(match.group(0).replace(",", ".")) if match else None


def extract_storage_gb(value):
    """
    "SSD 1 To" -> 1024 ; "SSD 512 Go" -> 512 ; "2 To" -> 2048 ; illisible -> 0
    """
    if not value:
        return 0
    match = re.search(r"(\d+)\s*(To|Go)", value, re.IGNORECASE)
    if not match:
        return 0
    size = int(match.group(1))
    return size * 1024 if match.group(2).lower() == "to" else size


def compact(attributes):
    """Retire les attributs absents de la fiche (None, 0, chaîne vide) : rien n'est envoyé à leur place."""
    return {k: v for k, v in attributes.items() if v not in (None, 0, "")}


def brand_key(value):
    """'BenQ' -> 'BENQ', 'LG Electronics' -> 'LGELECTRONICS' : comparaison sans casse ni ponctuation."""
    return re.sub(r"[^A-Z0-9]", "", value.upper())


def fetch_allowed_brands():
    r = requests.get(OPENAPI_URL, timeout=10)
    r.raise_for_status()
    brand_schema = r.json()["components"]["schemas"]["ProductCreateRequestDto"]["properties"]["brand"]
    return brand_schema["enum"]


def resolve_brand(product, allowed_brands):
    """
    Marque de la fiche LDLC ("Marque : Apple") sinon premier mot du titre, rapprochée de l'enum Brand
    du backend. Une marque absente de l'enum fait sauter le produit (l'API répondrait 400) au lieu de
    l'ancien repli sur ASUS, qui créait des produits avec une fausse marque.
    """
    by_key = {brand_key(b): b for b in allowed_brands}
    candidates = [spec(product["attributes"], "marque")]
    candidates += product["name"].split()[:1]
    for candidate in filter(None, candidates):
        key = brand_key(candidate)
        if key in by_key:
            return by_key[key]
        # "LG Electronics" -> LG, "Samsung Electronics" -> SAMSUNG
        first_word = brand_key(candidate.split()[0])
        if first_word in by_key:
            return by_key[first_word]
    raise SkipProduct(f"marque inconnue du backend : {spec(product['attributes'], 'marque') or product['name'].split()[0]}")


# -----------------------------
# MAPPINGS LDLC -> JSON Schema de catégorie (ProductCategorySchemaInitializer)
# -----------------------------
def map_attributes_ldlc_to_computer(raw):
    """
    Convertit les attributs LDLC en attributs conformes au JSON Schema
    de la catégorie COMPUTER (ProductCategorySchemaEntity).
    """

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
            spec(raw, "norme(s) réseau sans-fil"),
            spec(raw, "technologie bluetooth"),
            spec(raw, "connecteur(s) disponible(s)"),
            spec(raw, "sans-fil"),
            spec(raw, "technologie intel vpro"),
        ]
        fields = [f for f in fields if f]
        return ", ".join(fields) if fields else "Unknown connectivity"

    return {
        "cpu": spec(raw, "processeur", "cpu") or "Unknown CPU",
        "gpu": spec(raw, "chipset graphique", "gpu") or "Unknown GPU",
        "ram": extract_int(spec(raw, "taille de la mémoire", "mémoire", "ram")),
        "os": spec(raw, "système d'exploitation", "os") or "Unknown OS",
        "connectivity": map_connectivity(raw),
        "displayType": spec(raw, "type d'écran", "type de dalle", "écran") or "Unknown display",
        "memory": extract_storage_gb(spec(raw, "configuration disque(s)", "disque fourni", "stockage", "memory")),
    }


def map_attributes_ldlc_to_desktop(raw):
    """
    Schéma DESKTOP (tour PC, sans écran) : cpu, ram (Go) et memory (Go) obligatoires ;
    gpu, os, connectivity, formFactor si la fiche les donne.
    LDLC : "Type de processeur", "Taille de la mémoire : 16 Go", "Disque fourni : SSD 1 To",
    "Chipset graphique", "Norme(s) réseau sans-fil", "Format du boitier : Moyen Tour".
    """
    attributes = compact({
        "cpu": spec(raw, "processeur", "type de processeur"),
        "gpu": spec(raw, "chipset graphique"),
        "ram": extract_int(spec(raw, "taille de la mémoire", "mémoire")),
        "os": spec(raw, "système d'exploitation"),
        "memory": extract_storage_gb(spec(raw, "configuration disque(s)", "disque fourni", "stockage")),
        "connectivity": spec(raw, "norme(s) réseau sans-fil"),
        "formFactor": spec(raw, "format du boitier", "format du boîtier", "format de pc"),
    })
    missing = [k for k in ("cpu", "ram", "memory") if k not in attributes]
    if missing:
        raise SkipProduct(f"attribut(s) obligatoire(s) absent(s) de la fiche : {missing}")
    return attributes


def map_attributes_ldlc_to_smartphone(raw):
    """
    Schéma SMARTPHONE (aucun attribut obligatoire) : os, cpu, storage (Go), screenSize (pouces),
    network ("5G"/"4G"). LDLC : "Système d'exploitation : Apple iOS 27", "Processeur : Apple A20 Pro",
    "Stockage : 1 To", "Taille d'écran : 7.6 pouces", "Fréquence : 5G".
    """
    return compact({
        "os": spec(raw, "système d'exploitation"),
        "cpu": spec(raw, "processeur", "type de processeur"),
        "storage": extract_storage_gb(spec(raw, "stockage", "capacité")),
        "screenSize": extract_float(spec(raw, "taille d'écran", "taille de l'écran")),
        "network": spec(raw, "fréquence"),
    })


def map_attributes_ldlc_to_monitor(raw):
    """
    Schéma MONITOR : resolution (string, obligatoire), refreshRate (integer >= 60, obligatoire).
    LDLC : "Résolution Max : 2560 x 1440 pixels", "Fréquence verticale maxi : 180 Hz".
    """
    resolution = spec(raw, "résolution max", "résolution")
    refresh_rate = extract_int(spec(raw, "fréquence verticale maxi", "fréquence de rafraîchissement"))
    if not resolution:
        raise SkipProduct("résolution absente de la fiche")
    if refresh_rate < 60:
        raise SkipProduct(f"fréquence absente ou < 60 Hz ({refresh_rate})")
    return {
        "resolution": re.sub(r"\s*pixels?$", "", resolution, flags=re.IGNORECASE),
        "refreshRate": refresh_rate,
    }


def map_attributes_ldlc_to_smartwatch(raw):
    """
    Schéma SMARTWATCH (aucun attribut obligatoire, les fiches LDLC des montres sont inégales) :
    os, caseSize (mm), caseMaterial, batteryLife (heures), cellular ("Oui"/"Non").
    Les attributs absents de la fiche ne sont pas envoyés (pas de valeur "Unknown").
    """
    return compact({
        "os": spec(raw, "système d'exploitation"),
        "caseSize": extract_int(spec(raw, "taille du boîtier")),
        "caseMaterial": spec(raw, "matière du boîtier"),
        "batteryLife": extract_int(spec(raw, "autonomie")),
        "cellular": spec(raw, "données cellulaires"),
    })


ATTRIBUTE_MAPPERS = {
    "COMPUTER": map_attributes_ldlc_to_computer,
    "DESKTOP": map_attributes_ldlc_to_desktop,
    "MONITOR": map_attributes_ldlc_to_monitor,
    "SMARTPHONE": map_attributes_ldlc_to_smartphone,
    "SMARTWATCH": map_attributes_ldlc_to_smartwatch,
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


def check_category_schemas(categories):
    """Une catégorie sans schéma côté backend ferait échouer chaque produit en 400 : on s'arrête avant."""
    missing = [c for c in categories if requests.get(CATEGORY_SCHEMA_URL.format(c), timeout=10).status_code != 200]
    if missing:
        raise Exception(f"Catégorie(s) sans schéma côté backend : {missing}. "
                        "Redémarre le backend (ProductCategorySchemaInitializer les crée au démarrage).")


# -----------------------------
# 2. Envoi API Spring
# -----------------------------
# L'upload S3 est fait côté backend (ProductManagementServiceImp.createWithImage
# -> S3Service.uploadFile, une fois par fichier), qui écrase de toute façon le champ
# "photos" du DTO avec les URLs réelles une fois les images reçues : pas besoin
# d'uploader nous-mêmes.
def send_product_to_api(product, token, allowed_brands):
    """Renvoie True si le produit est créé. Lève SkipProduct s'il ne peut pas être envoyé."""
    headers = {
        "Authorization": f"Bearer {token}"
    }

    category = product.get("category", DEFAULT_CATEGORY)
    image_paths = product["images"]  # 1 à N photos, pas de nombre fixe
    if not image_paths:
        raise SkipProduct("aucune image")
    brand = resolve_brand(product, allowed_brands)
    mapped_attributes = ATTRIBUTE_MAPPERS[category](product["attributes"])

    product_dto = {
        "name": product["name"],
        "price": product["price"],
        "brand": brand,
        "category": category,
        "photos": [],  # placeholder, écrasé par le backend avec les URLs S3 réelles
        "stock": 10000,
        "description": product["description"],
        "attributes": mapped_attributes
    }

    log.info(f"📄 DTO envoyé : {json.dumps(product_dto, ensure_ascii=False)[:300]}...")

    # Plusieurs parts multipart partageant le même nom "images" -> liée côté Spring
    # à List<MultipartFile> images (voir ProductManagementAdminController).
    opened_files = [open(path, "rb") for path in image_paths]
    try:
        files = [
            ("images", (os.path.basename(path), fh, "image/jpeg"))
            for path, fh in zip(image_paths, opened_files)
        ]
        files.append(("product", ("product", json.dumps(product_dto), "application/json")))

        log.info(f"📦 Envoi produit : {product['name']} ({len(image_paths)} photo(s))")
        r = requests.post(API_URL, headers=headers, files=files)

        if r.status_code not in (200, 201):
            log.error(f"❌ Erreur API : {r.status_code} - {r.text}")
            return False
        log.info(f"✅ Produit créé : {product['name']}")
        return True
    finally:
        for fh in opened_files:
            fh.close()


# -----------------------------
# MAIN
# -----------------------------
def main(categories_filter):
    log.info("🚀 Démarrage ingestion complète")

    with open(PRODUCTS_JSON, "r", encoding="utf-8") as f:
        products = json.load(f)
    if categories_filter:
        products = [p for p in products if p.get("category", DEFAULT_CATEGORY) in categories_filter]

    categories = sorted({p.get("category", DEFAULT_CATEGORY) for p in products})
    unknown = [c for c in categories if c not in ATTRIBUTE_MAPPERS]
    if unknown:
        raise Exception(f"Catégorie(s) sans mapping d'attributs dans ce script : {unknown}")
    check_category_schemas(categories)
    allowed_brands = fetch_allowed_brands()
    token = get_access_token()

    created, failed = Counter(), Counter()
    skipped = Counter()
    for p in products:
        category = p.get("category", DEFAULT_CATEGORY)
        log.info("\n==============================")
        log.info(f"🖥️  Traitement [{category}] : {p['name']}")
        log.info("==============================")
        try:
            if send_product_to_api(p, token, allowed_brands):
                created[category] += 1
            else:
                failed[category] += 1
        except SkipProduct as e:
            log.warning(f"⏭️  Ignoré : {e}")
            skipped[f"{category} — {e}"] += 1

    log.info("🎉 Ingestion terminée")
    for category in categories:
        log.info(f"   {category} : {created[category]} créé(s), {failed[category]} en erreur API")
    if skipped:
        log.info("   Produits ignorés (ajouter la marque à l'enum Brand du backend si besoin) :")
        for reason, count in skipped.most_common():
            log.info(f"     {count:4d} × {reason}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Envoie products.json à l'API admin du backend")
    parser.add_argument("--categories", nargs="+", choices=list(ATTRIBUTE_MAPPERS),
                        help="Catégories à envoyer (défaut : toutes celles du JSON)")
    main(parser.parse_args().categories)

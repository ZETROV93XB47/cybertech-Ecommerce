# -*- coding: utf-8 -*-
"""
Scrape des produits LDLC (PC portables, tours PC, écrans, smartphones, montres connectées)
vers products.json + images/.

Usage :
    python laptopScrapper.py                                  # toutes les catégories
    python laptopScrapper.py --categories MONITOR SMARTWATCH  # seulement certaines
    python laptopScrapper.py --limit 50                       # 50 produits max par catégorie
    python laptopScrapper.py --delay 5                        # pause entre deux pages (secondes)

Reprise : products.json est réécrit après CHAQUE produit, et un produit déjà présent (même
sourceUrl) n'est pas re-scrapé ; les images déjà téléchargées ne sont pas re-téléchargées et la
liste des URL de chaque catégorie est gardée dans urls_<catégorie>.json. Relancer la même commande
après une interruption reprend donc où le script s'était arrêté, sans rien perdre ni re-solliciter
le site pour ce qui est déjà récupéré.

Blocage : si LDLC répond 403 / 429 (ou une page de refus), le script s'arrête aussitôt, en ayant
sauvegardé tout ce qu'il a récupéré. Il ne cherche pas à contourner le blocage : attendre qu'il soit
levé, puis relancer avec un --delay plus grand.

Chaque produit du JSON porte sa "category" (clé de ProductCategorySchemaEntity côté backend) ;
dataLoader.py s'en sert pour choisir le mapping d'attributs et l'envoyer à l'API.
"""
from playwright.sync_api import sync_playwright
import argparse
import time
import json
import os
import requests
import re

BASE_URL = "https://www.ldlc.com"

# Clé de catégorie backend -> page liste LDLC. Une URL peut porter un filtre LDLC après
# l'identifiant de catégorie (ex. "+fv1046-5906.html" = format Tour) : voir page_url().
CATEGORY_URLS = {
    "COMPUTER": BASE_URL + "/informatique/ordinateur-portable/pc-portable/c4265/",
    "DESKTOP": BASE_URL + "/informatique/ordinateur-de-bureau/pc-de-marque/c4250/+fv1046-5906.html",
    "MONITOR": BASE_URL + "/informatique/peripherique-pc/moniteur-pc/c4623/",
    "SMARTPHONE": BASE_URL + "/telephonie/telephonie-portable/mobile-smartphone/c4416/",
    "SMARTWATCH": BASE_URL + "/objets-connectes/loisirs/montre-connectee/c5962/",
}

OUTPUT_JSON = "products.json"
IMAGES_DIR = "images"
URLS_CACHE = "urls_{}.json"

# Pause par défaut entre deux pages chargées (liste ou fiche), pour ne pas surcharger le site.
DEFAULT_DELAY_S = 3.0

# Réponses qui signifient "on ne veut plus de toi" : on s'arrête net.
BLOCKED_STATUSES = {403, 429}
BLOCKED_PAGE_MARKERS = ("access denied", "accès refusé", "too many requests", "captcha")

# Signatures des formats d'image acceptés (la galerie LDLC contient aussi des vignettes vidéo
# qui renvoient une page HTML : elles ne doivent pas finir en .jpg).
IMAGE_SIGNATURES = (b"\xff\xd8\xff", b"\x89PNG", b"RIFF", b"GIF8")


class BlockedError(Exception):
    """LDLC refuse nos requêtes : on arrête tout (après sauvegarde)."""


def sanitize_filename(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_-]", "_", name)


def is_image_file(path):
    try:
        with open(path, "rb") as f:
            return f.read(4).startswith(IMAGE_SIGNATURES)
    except OSError:
        return False


def download_image(url, filename):
    """
    Télécharge une image si elle n'est pas déjà là. Renvoie True si le fichier final est une image.
    Une réponse qui n'est pas une image (vignette vidéo, page d'erreur) est ignorée.
    """
    if is_image_file(filename):
        return True
    try:
        response = requests.get(url, timeout=10)
    except Exception as e:
        print("Erreur téléchargement image:", e)
        return False
    if response.status_code in BLOCKED_STATUSES:
        raise BlockedError(f"HTTP {response.status_code} sur l'image {url}")
    if response.status_code != 200 or not response.content.startswith(IMAGE_SIGNATURES):
        print(f"  image ignorée (pas une image : HTTP {response.status_code}, {response.headers.get('content-type')}) : {url}")
        if os.path.exists(filename):
            os.remove(filename)  # reste d'un précédent run qui avait enregistré du HTML
        return False
    with open(filename, "wb") as f:
        f.write(response.content)
    return True


def goto(page, url, delay):
    """Charge une page ; lève BlockedError si LDLC refuse la requête."""
    time.sleep(delay)
    response = page.goto(url)
    page.wait_for_load_state("networkidle")
    status = response.status if response else None
    if status in BLOCKED_STATUSES:
        raise BlockedError(f"HTTP {status} sur {url}")
    title = (page.title() or "").lower()
    if any(marker in title for marker in BLOCKED_PAGE_MARKERS):
        raise BlockedError(f"page de refus sur {url} (titre : {page.title()!r})")


def parse_price(raw_price: str) -> float:
    """
    "1 299€95" -> 1299.95 ; "349€" -> 349.0 ; "1 299,95 €" -> 1299.95.
    (L'ancienne version retirait toujours 2 caractères, ce qui cassait un prix sans centimes.)
    """
    clean = re.sub(r"[\s \xa0]", "", raw_price)
    match = re.search(r"(\d+)(?:€(\d{2})|[.,](\d{2})€?)?", clean)
    if not match:
        raise ValueError(f"Prix illisible : {raw_price!r}")
    cents = match.group(2) or match.group(3) or "00"
    return float(f"{match.group(1)}.{cents}")


def extract_product_data(page, product_url, category, delay):
    goto(page, product_url, delay)

    # --- TITRE ---
    title_el = page.query_selector("h1")
    title = title_el.inner_text().strip() if title_el else ""

    # --- PRIX ---
    price_el = page.query_selector(".price")
    price = parse_price(price_el.inner_text().strip() if price_el else "")

    # --- DESCRIPTION (1ère section) ---
    first_section = page.query_selector(".details-pdt-desc section")
    description = first_section.inner_text().strip() if first_section else ""

    # --- FICHE TECHNIQUE ---
    specs = {}

    tab_btn = page.query_selector("a[href='#specs-tech']")
    if tab_btn:
        tab_btn.click()
        time.sleep(1)

    rows = page.query_selector_all("#specs-tech #product-parameters tr")

    for row in rows:
        label_el = row.query_selector(".label h3")
        value_el = row.query_selector(".checkbox")

        if label_el and value_el:
            key = label_el.inner_text().strip().lower()
            val = value_el.inner_text().strip()
            # Un libellé répété (ex. "modèle") garde sa première valeur.
            specs.setdefault(key, val)

    # --- IMAGES ---
    image_urls = []

    main_img = page.query_selector("#productphoto .product a.photodefault")
    if main_img:
        hd_url = main_img.get_attribute("href")
        if hd_url:
            image_urls.append(hd_url)

    thumbs = page.query_selector_all("#productphoto .zoom li.vignette a")
    for t in thumbs:
        hd = t.get_attribute("href")
        if hd and hd not in image_urls:
            image_urls.append(hd)

    category_images_dir = f"{IMAGES_DIR}/{category.lower()}"
    os.makedirs(category_images_dir, exist_ok=True)
    downloaded_images = []
    base_name = sanitize_filename(title) or "product"

    for i, url in enumerate(image_urls):
        filename = f"{category_images_dir}/{base_name}_{i}.jpg"
        if download_image(url, filename):
            downloaded_images.append(filename)

    return {
        "name": title,
        "price": price,
        "category": category,
        "description": description,
        "attributes": specs,
        "images": downloaded_images,
        "sourceUrl": product_url,
    }


def page_url(category_url, page_num):
    """
    Page N d'une liste LDLC : "pageN/" juste après l'identifiant de catégorie, filtre conservé.
    .../c4416/ -> .../c4416/page2/ ; .../c4250/+fv1046-5906.html -> .../c4250/page2/+fv1046-5906.html
    """
    if page_num == 1:
        return category_url
    return re.sub(r"(/c\d+/)", rf"\g<1>page{page_num}/", category_url, count=1)


def spec_value(product, label):
    attributes = {k.replace("’", "'"): v for k, v in product["attributes"].items()}
    value = attributes.get(label)
    return value.strip().lower() if value else None


def is_wanted(product):
    """
    Certaines listes LDLC mélangent d'autres produits : on ne garde que les montres (pas les
    accessoires) et les smartphones (pas les téléphones à clapet ou "senior").
    """
    if product["category"] == "SMARTWATCH":
        accessory_type = spec_value(product, "type d'accessoire")
        return accessory_type is None or accessory_type == "montre"
    if product["category"] == "SMARTPHONE":
        phone_type = spec_value(product, "type de téléphone")
        return phone_type is None or phone_type == "smartphone"
    return True


def load_json(path, default):
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    """Écriture atomique : un arrêt brutal pendant l'écriture ne corrompt jamais le fichier."""
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)
    os.replace(tmp, path)


def collect_product_urls(page, category, limit, delay):
    """URL des fiches d'une catégorie, gardées dans urls_<catégorie>.json pour la reprise."""
    cache = URLS_CACHE.format(category.lower())
    cached = load_json(cache, None)
    if cached:
        print(f"{len(cached)} URL relues depuis {cache} (supprimer le fichier pour re-parcourir la liste)")
        return cached[:limit] if limit else cached

    category_url = CATEGORY_URLS[category]
    goto(page, category_url, delay)

    # La pagination n'affiche que quelques numéros (1 2 3 … 30) : on prend le plus grand
    # et on parcourt toutes les pages jusqu'à lui.
    last_page = 1
    for link in page.query_selector_all(".pagination a[data-page]"):
        num = link.get_attribute("data-page")
        if num and num.isdigit():
            last_page = max(last_page, int(num))
    print(f"Pages détectées : 1 à {last_page}")

    product_urls = []
    for page_num in range(1, last_page + 1):
        url = page_url(category_url, page_num)
        print(f"  Page {page_num} : {url}")
        if page_num > 1:
            goto(page, url, delay)

        for item in page.query_selector_all("li.pdt-item a[href*='/fiche/']"):
            href = item.get_attribute("href")
            if href and BASE_URL + href not in product_urls:
                product_urls.append(BASE_URL + href)

        if limit and len(product_urls) >= limit:
            break

    if not limit:
        # Liste complète seulement : une liste tronquée par --limit ne doit pas servir à la reprise.
        save_json(cache, product_urls)
    return product_urls[:limit] if limit else product_urls


def scrape(categories, limit, delay):
    # Reprise : on repart des produits déjà sauvegardés (toutes catégories confondues).
    all_products = load_json(OUTPUT_JSON, [])
    done_urls = {p["sourceUrl"] for p in all_products}
    print(f"{len(all_products)} produit(s) déjà dans {OUTPUT_JSON}")

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        try:
            for category in categories:
                print(f"\n===== {category} : {CATEGORY_URLS[category]} =====")
                product_urls = collect_product_urls(page, category, limit, delay)
                todo = [u for u in product_urls if u not in done_urls]
                print(f"{len(product_urls)} produit(s) pour {category}, {len(todo)} restant(s) à scraper")

                for idx, url in enumerate(todo, start=1):
                    print(f"\n[{category} {idx}/{len(todo)}] {url}")
                    try:
                        data = extract_product_data(page, url, category, delay)
                    except BlockedError:
                        raise
                    except Exception as e:
                        print(f"Erreur sur {url} : {e}")
                        continue
                    if is_wanted(data):
                        all_products.append(data)
                        done_urls.add(url)
                        save_json(OUTPUT_JSON, all_products)
                    else:
                        print("  -> ignoré (hors catégorie : accessoire, téléphone non smartphone)")
        except BlockedError as e:
            print(f"\nSTOP : LDLC refuse les requêtes ({e}).")
            print(f"   {len(all_products)} produit(s) sauvegardé(s) dans {OUTPUT_JSON}.")
            print("   Attendre la levée du blocage, puis relancer la même commande (avec un --delay plus grand) : "
                  "le script reprendra où il s'est arrêté.")
        finally:
            browser.close()

    save_json(OUTPUT_JSON, all_products)
    print(f"\nTerminé. {len(all_products)} produits dans {OUTPUT_JSON}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Scrape LDLC vers products.json")
    parser.add_argument("--categories", nargs="+", choices=list(CATEGORY_URLS), default=list(CATEGORY_URLS),
                        help="Catégories à scraper (défaut : toutes)")
    parser.add_argument("--limit", type=int, default=0,
                        help="Nombre max de produits par catégorie (défaut : tous)")
    parser.add_argument("--delay", type=float, default=DEFAULT_DELAY_S,
                        help=f"Pause en secondes avant chaque page chargée (défaut : {DEFAULT_DELAY_S})")
    args = parser.parse_args()
    scrape(args.categories, args.limit, args.delay)

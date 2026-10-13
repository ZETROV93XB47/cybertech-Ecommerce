# -*- coding: utf-8 -*-
"""
Scrape des produits LDLC (PC portables, écrans, montres connectées) vers products.json + images/.

Usage :
    python laptopScrapper.py                                  # les 3 catégories
    python laptopScrapper.py --categories MONITOR SMARTWATCH  # seulement certaines
    python laptopScrapper.py --limit 50                       # 50 produits max par catégorie

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

# Clé de catégorie backend -> page liste LDLC.
CATEGORY_URLS = {
    "COMPUTER": BASE_URL + "/informatique/ordinateur-portable/pc-portable/c4265/",
    "MONITOR": BASE_URL + "/informatique/peripherique-pc/moniteur-pc/c4623/",
    "SMARTWATCH": BASE_URL + "/objets-connectes/loisirs/montre-connectee/c5962/",
}

OUTPUT_JSON = "products.json"
IMAGES_DIR = "images"

# Pause entre deux fiches produit, pour ne pas marteler le site.
DELAY_BETWEEN_PRODUCTS_S = 0.5


def sanitize_filename(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_-]", "_", name)


def download_image(url, filename):
    try:
        img = requests.get(url, timeout=10).content
        with open(filename, "wb") as f:
            f.write(img)
        return True
    except Exception as e:
        print("Erreur téléchargement image:", e)
        return False


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


def extract_product_data(page, product_url, category):
    page.goto(product_url)
    page.wait_for_load_state("networkidle")

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

    category_images_dir = os.path.join(IMAGES_DIR, category.lower())
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


def is_wanted(product):
    """La catégorie montres peut contenir des accessoires : on ne garde que les montres."""
    if product["category"] != "SMARTWATCH":
        return True
    accessory_type = product["attributes"].get("type d'accessoire") or product["attributes"].get("type d’accessoire")
    return accessory_type is None or accessory_type.strip().lower() == "montre"


def collect_product_urls(page, category_url, limit):
    page.goto(category_url)
    page.wait_for_load_state("networkidle")

    # La pagination n'affiche que quelques numéros (1 2 3 … 30) : on prend le plus grand
    # et on parcourt toutes les pages jusqu'à lui.
    last_page = 1
    for link in page.query_selector_all(".pagination a[data-page]"):
        num = link.get_attribute("data-page")
        if num and num.isdigit():
            last_page = max(last_page, int(num))
    print(f"Pages détectées : 1 → {last_page}")

    product_urls = []
    for page_num in range(1, last_page + 1):
        url = category_url if page_num == 1 else category_url + f"page{page_num}/"
        print(f"  Page {page_num} : {url}")
        if page_num > 1:
            page.goto(url)
            page.wait_for_load_state("networkidle")

        for item in page.query_selector_all("li.pdt-item a[href*='/fiche/']"):
            href = item.get_attribute("href")
            if href and BASE_URL + href not in product_urls:
                product_urls.append(BASE_URL + href)

        if limit and len(product_urls) >= limit:
            break

    return product_urls[:limit] if limit else product_urls


def scrape(categories, limit):
    all_products = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()

        for category in categories:
            print(f"\n===== {category} : {CATEGORY_URLS[category]} =====")
            product_urls = collect_product_urls(page, CATEGORY_URLS[category], limit)
            print(f"{len(product_urls)} produit(s) trouvé(s) pour {category}")

            kept = 0
            for idx, url in enumerate(product_urls, start=1):
                print(f"\n[{category} {idx}/{len(product_urls)}] {url}")
                try:
                    data = extract_product_data(page, url, category)
                    if is_wanted(data):
                        all_products.append(data)
                        kept += 1
                    else:
                        print("  → ignoré (accessoire, pas une montre)")
                except Exception as e:
                    print(f"Erreur sur {url} : {e}")
                time.sleep(DELAY_BETWEEN_PRODUCTS_S)
            print(f"\n{kept} produit(s) {category} gardé(s)")

        browser.close()

    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(all_products, f, indent=4, ensure_ascii=False)

    print(f"\nScraping terminé. {len(all_products)} produits sauvegardés dans {OUTPUT_JSON}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Scrape LDLC vers products.json")
    parser.add_argument("--categories", nargs="+", choices=list(CATEGORY_URLS), default=list(CATEGORY_URLS),
                        help="Catégories à scraper (défaut : toutes)")
    parser.add_argument("--limit", type=int, default=0,
                        help="Nombre max de produits par catégorie (défaut : tous)")
    args = parser.parse_args()
    scrape(args.categories, args.limit)

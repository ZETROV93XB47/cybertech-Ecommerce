# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright
import time
import json
import os
import requests
import re

BASE_URL = "https://www.ldlc.com"
CATEGORY_URL = BASE_URL + "/informatique/ordinateur-portable/pc-portable/c4265/"
OUTPUT_JSON = "products.json"
IMAGES_DIR = "images"

os.makedirs(IMAGES_DIR, exist_ok=True)


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


def extract_product_data(page, product_url):
    page.goto(product_url)
    page.wait_for_load_state("networkidle")

    # --- TITRE ---
    title_el = page.query_selector("h1")
    title = title_el.inner_text().strip() if title_el else ""

    # --- PRIX (entier) ---
    price_el = page.query_selector(".price")
    raw_price = price_el.inner_text().strip() if price_el else "0"

    clean = raw_price
    clean = clean.replace("€", "")
    clean = clean.replace(",", ".")
    clean = clean.replace("\u202f", "")  # fine space
    clean = clean.replace("\xa0", "")    # NBSP
    clean = clean.replace(" ", "")       # espace normal

    if len(clean) > 2:
        clean = clean[:-2]

    price_int = int(clean)

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
            specs[key] = val

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

    downloaded_images = []
    base_name = sanitize_filename(title) or "product"

    for i, url in enumerate(image_urls):
        filename = f"{IMAGES_DIR}/{base_name}_{i}.jpg"
        if download_image(url, filename):
            downloaded_images.append(filename)

    return {
        "name": title,
        "price": price_int,
        "description": description,
        "attributes": specs,
        "images": downloaded_images,
        "sourceUrl": product_url,
    }


def scrape_all():
    all_products = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()

        # Page principale
        page.goto(CATEGORY_URL)
        page.wait_for_load_state("networkidle")

        # --- RÉCUPÉRER LES NUMÉROS DE PAGES ---
        pagination_links = page.query_selector_all(".pagination a[data-page]")
        pages = {1}

        for link in pagination_links:
            num = link.get_attribute("data-page")
            if num and num.isdigit():
                pages.add(int(num))

        pages = sorted(list(pages))
        print(f"Pages détectées : {pages}")

        # --- RÉCUPÉRER TOUS LES LIENS PRODUITS ---
        product_urls = set()

        for page_num in pages:
            if page_num == 1:
                url = CATEGORY_URL
            else:
                url = CATEGORY_URL + f"page{page_num}/"

            print(f"\nScraping page {page_num} : {url}")
            page.goto(url)
            page.wait_for_load_state("networkidle")

            items = page.query_selector_all("li.pdt-item a[href*='/fiche/']")

            for item in items:
                href = item.get_attribute("href")
                if href:
                    product_urls.add(BASE_URL + href)

        print(f"\nTotal produits trouvés : {len(product_urls)}")

        # --- SCRAPER CHAQUE PRODUIT ---
        for idx, url in enumerate(sorted(product_urls), start=1):
            print(f"\n[{idx}/{len(product_urls)}] Scraping produit : {url}")
            try:
                data = extract_product_data(page, url)
                all_products.append(data)
            except Exception as e:
                print(f"Erreur sur {url} : {e}")

        browser.close()

    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(all_products, f, indent=4, ensure_ascii=False)

    print(f"\nScraping terminé. {len(all_products)} produits sauvegardés dans {OUTPUT_JSON}")


if __name__ == "__main__":
    scrape_all()
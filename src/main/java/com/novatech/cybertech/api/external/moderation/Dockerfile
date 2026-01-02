FROM python:3.10-slim

WORKDIR /app

# On installe les dépendances système minimales
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

# Étape cruciale : installer la version CPU de Torch pour gagner plusieurs Go
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

# Prétélécharger le modèle pendant le build pour éviter qu'il ne se télécharge au démarrage du pod
RUN python -c "from transformers import pipeline; pipeline('text-classification', model='unitary/toxic-bert')"

EXPOSE 5000
CMD ["python", "app.py"]
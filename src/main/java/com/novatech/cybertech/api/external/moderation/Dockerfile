FROM python:3.10-slim

WORKDIR /app

# On installe les dépendances système minimales
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

# Étape cruciale : installer la version CPU de Torch pour gagner plusieurs Go
# --extra-index-url (et non --index-url) : garde PyPI accessible pour les dépendances
# transitives de torch (sympy, networkx, typing-extensions...) qui ne sont pas toutes
# mirrorées sur l'index PyTorch, sinon leur build depuis les sources échoue faute de
# backend (flit_core) introuvable. Le pin exact "+cpu" évite qu'un torch CUDA (~2 Go)
# ne soit résolu depuis PyPI à la place.
RUN pip install --no-cache-dir torch==2.6.0+cpu --extra-index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

# Prétélécharger le modèle pendant le build pour éviter qu'il ne se télécharge au démarrage du pod
RUN python -c "from transformers import pipeline; pipeline('text-classification', model='unitary/toxic-bert')"

EXPOSE 5000
CMD ["python", "app.py"]
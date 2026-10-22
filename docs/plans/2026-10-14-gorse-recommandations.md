# Plan à exécuter : remettre en marche les recommandations Gorse

> Rédigé le 2026-10-14, validé par le propriétaire du projet. À lancer dans une nouvelle
> conversation : « Exécute `docs/plans/2026-10-14-gorse-recommandations.md` ».
> Respecter `CLAUDE.md` (PLAN.md + progress.md, tests verts avant commit, conventions du projet).

## Constat (mesuré sur le stack de dev le 2026-10-14)

Gorse tourne (`zhenghaoz/gorse-in-one:latest`, binaire **v0.5.4**, ports 8088/8089) mais ne
recommande rien d'utile :

- `docker/gorse/config.toml` ne définit que `[recommend.data_source]` (types positifs / lus). Avec
  cette version, tout le reste prend des valeurs par défaut vides. Lu via
  `GET http://localhost:8088/api/dashboard/config` :
  - `recommend.collaborative.type = "none"` → filtrage collaboratif désactivé ;
  - `recommend.item-to-item = null`, `recommend.user-to-user = null` → pas de « produits similaires » ;
  - `recommend.non-personalized = null` → aucun classement « populaire » (`GET /api/popular` → 404 sur
    cette version) ;
  - `recommend.ranker.recommenders = ["latest"]` → les « recommandations » ne sont que les produits
    les plus récents.
- `GET /api/dashboard/stats` : `NumUsers 112`, `NumItems 1605`, mais **`NumTotalPosFeedback 0`** :
  Gorse n'a reçu aucun événement positif. Même bien configuré, il n'aurait rien pour apprendre.
- L'image est en `:latest` : le format de configuration a changé sans qu'on le voie (l'ancienne
  remarque de `progress.md` sur un type `star` vient du format d'avant).
- Piste pour les événements manquants : `GorseSyncTasklet` ne relit que les
  `cybertech.gorse.sync.job.lookback-minutes=20` dernières minutes (cron toutes les 15 min). Tout
  événement émis quand l'appli était arrêtée n'est jamais envoyé.

Côté backend : `GorseClient` appelle `/api/recommend/{user}` et `/api/latest` ;
`RecommendationServiceImp` replie personnalisé → latest → best-sellers.

## Étapes

### 1. Figer l'image

- `docker-compose.yml` : remplacer `zhenghaoz/gorse-in-one:latest` par le tag correspondant à la
  v0.5.4 (vérifier qu'il existe : `docker manifest inspect zhenghaoz/gorse-in-one:<tag>`), recréer
  le conteneur, contrôler `docker logs gorse` (version + config chargée).

### 2. Diagnostiquer pourquoi aucun événement n'arrive (prérequis du reste)

- Compter les événements produits dans Mongo (collection des `UserEvent`) et leurs types.
- Lancer le job de synchro à la main (ou attendre un tick) et lire ses logs : nombre de feedbacks
  envoyés, réponse de Gorse (`POST /api/feedback`), erreurs éventuelles.
- Vérifier que `GorseMapper` produit des `FeedbackType` égaux aux noms de la config, et que Gorse
  connaît les `UserId` / `ItemId` envoyés.
- Corriger la cause. Si c'est la fenêtre de 20 min : passer à une synchro **incrémentale par
  curseur** (dernier timestamp envoyé, persistant), avec un rattrapage complet au premier passage.
  L'insertion de feedback Gorse est idempotente (même user/item/type/timestamp).
- Critère : `NumTotalPosFeedback > 0` après avoir généré quelques événements (panier, wishlist,
  achat) sur le front ou via `POST /api/v1/events/consume-event`.

### 3. Compléter `docker/gorse/config.toml`

Vérifier d'abord la **syntaxe exacte de la v0.5.4** (doc de la version, ou essai sur le conteneur
puis relecture de `/api/dashboard/config`), puis ajouter :

- filtrage collaboratif activé (type matrix factorization de cette version) ;
- un recommandeur non personnalisé « popular » : produits les plus achetés / ajoutés au panier sur
  30 jours (score = nombre de feedbacks `PURCHASE` / `ADD_TO_CART`, fenêtre glissante) ;
- un recommandeur item-to-item « similar » : produits achetés / aimés par les mêmes utilisateurs ;
- ranker : collaboratif → similar → popular → latest.

Garder les commentaires du fichier (pourquoi chaque bloc). Recréer le conteneur et relire
`/api/dashboard/config` : chaque bloc doit apparaître non nul.

### 4. Backend

- `GorseClient` : lecture du classement populaire (endpoint non personnalisé de la v0.5.4,
  `/api/non-personalized/{name}` à confirmer), même résilience que `getLatestItems` (`gorseRead`).
- `RecommendationServiceImp` : repli personnalisé → popular → latest → best-sellers.
- Si utile pour le front : endpoint public « produits populaires » (suivre le pattern
  `RecommendationController` + `*ApiSpec`, constantes, OpenAPI régénéré, Postman).

### 5. Tests et vérification

- `GorseConfigConsistencyTest` : étendre pour que tout `FeedbackType` cité dans la config (positifs,
  lus, et ceux utilisés dans les recommandeurs) existe dans `UserEventType`.
- Tests unitaires `GorseClient`, `RecommendationServiceImp` (chaîne de repli complète), tasklet de
  synchro (curseur si ajouté).
- Vérification réelle : générer des événements pour 2-3 utilisateurs, attendre l'entraînement
  (`fit_period`), contrôler `/api/recommend/{user}` (plus seulement les derniers produits) et le
  classement populaire.
- `./mvnw test` vert ; ITs concernées si touchées.

### 6. Clôture

- `progress.md` : remplacer la remarque « Gorse star » par le nouvel état.
- Commit séparé (`fix(recommendations): ...`), fichiers de la tâche seulement.

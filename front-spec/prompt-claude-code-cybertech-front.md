# Mission : initialiser le front Next.js de cybertech avec Spec Kit

Tu es dans un **dossier vide** destiné à devenir le repo du **front Next.js** de **cybertech**, un projet e-commerce pédagogique. Le backend existe déjà dans un autre repo :

- **Backend** : `[CHEMIN_LOCAL_DU_BACKEND ou URL_GITHUB]` ← à remplacer avant de lancer.
- Stack backend : Spring Boot, PostgreSQL, Redis (panier, stats produits), JWT, Docker, un microservice Python de modération de commentaires par IA, déploiement Kubernetes via Helm. L'architecture évolue vers API Gateway + microservices.

Ta mission, dans l'ordre :
1. Initialiser ce repo.
2. Analyser le backend en profondeur, **en lecture seule**.
3. Produire la documentation d'intégration.
4. Installer Spec Kit et rédiger les prompts Spec Kit qui serviront à construire le front.

**Tu ne codes pas encore le front.** Le scaffolding Next.js et l'implémentation seront faits plus tard via `/speckit.implement`.

## Règles générales

- **Ne modifie jamais le repo backend.** Si c'est une URL GitHub, clone-le dans un dossier temporaire hors de ce repo (par exemple `../cybertech-backend-readonly`), sans jamais le committer ici.
- **N'invente rien.** Si une information n'est pas déductible du code, écris `⚠️ À CONFIRMER` et ajoute-la aux questions finales.
- Cite toujours la source : `chemin/Fichier.java:ligne` ou `Classe#méthode`.
- En cas de point bloquant (backend introuvable, versions contradictoires…), pose-moi la question **avant** de continuer.
- Rédige en **français**. Les noms techniques restent en anglais.
- Utilise ta liste de tâches et coche chaque phase terminée.

---

## Phase 0 — Initialisation du repo

1. `git init`, branche `main`.
2. Crée `README.md` (description du projet, lien vers le backend), un `.gitignore` adapté à Node/Next.js et le dossier `docs/backend/`.
3. Déplace ce fichier de prompt dans `docs/initial-prompt.md` pour en garder la trace.
4. Premier commit : `chore: init repository`.

## Phase 1 — Scan du backend

Explore tout le repo backend et identifie :

1. **Build et versions** : `pom.xml` / `build.gradle`, versions de Java, Spring Boot, dépendances clés (Spring Security, JPA, Redis, springdoc, validation, MapStruct, Lombok…).
2. **Structure** : modules et packages, organisation en couches (controller / service / repository / dto / entity / mapper / config / security / exception).
3. **Configuration** : `application*.yml|properties`, profils, variables d'environnement, ports, context-path, **CORS**.
4. **Sécurité** :
   - filtres JWT ;
   - endpoints publics vs protégés ;
   - rôles et autorités ;
   - format du token (claims, durée de vie) ;
   - présence ou non d'un **refresh token** ;
   - logout ;
   - CSRF.
5. **Redis** : stockage du panier et des stats (clés, structures, sérialisation, TTL), verrous distribués.
6. **Microservices annexes** : service Python de modération (appel synchrone ou asynchrone, impact sur le parcours utilisateur), éventuelle API Gateway, Spring Cloud Config, Vault.
7. **Gestion des erreurs** : `@ControllerAdvice`, format JSON des erreurs, erreurs de validation.
8. **Conventions transverses** : pagination et tri, formats de dates, montants et devises, URLs des images, versioning d'API.
9. **Infra** : `docker-compose`, Dockerfiles, chart Helm (services, ports, ingress).
10. **OpenAPI** : springdoc est-il présent ? Si oui, compare la spec au code réel.

## Phase 2 — Cartographie des endpoints

Crée `docs/backend/api-map.md`. Groupe les endpoints **par domaine métier** (auth, utilisateurs, produits, catégories, panier, commandes, commentaires…), avec un tableau par endpoint :

| Méthode | Chemin | Controller#méthode | Auth / rôle | Params (path/query) | Body (DTO + champs + types + contraintes) | Réponse (DTO + champs) | Codes HTTP et erreurs | Pagination | Notes |

Exigences :
- Détaille chaque DTO **champ par champ**, avec ses contraintes Bean Validation. Elles serviront à générer les schémas **Zod** côté front.
- Signale les incohérences : endpoints mal sécurisés, entités JPA exposées, codes HTTP inattendus, nommage incohérent.
- Ajoute une section **« Endpoints manquants pour un front e-commerce complet »**.

## Phase 3 — Compréhension fonctionnelle

Crée `docs/backend/analysis.md` avec :

1. L'architecture : schéma Mermaid des composants et des flux.
2. Le modèle de domaine : schéma Mermaid des entités et relations.
3. Le **flux d'authentification complet** : inscription, login, token, expiration, refresh, logout.
4. Le **cycle de vie du panier** : création, ajout, mise à jour, fusion anonyme → connecté, expiration, conversion en commande.
5. Les parcours utilisateur possibles aujourd'hui, et ceux qui ne le sont pas encore.
6. Les contraintes pour le front : CORS, rate limiting, dépendance au service de modération.
7. Les risques et points d'attention pour l'intégration.

## Phase 4 — Spec Kit

1. Installe Spec Kit dans ce repo :
   ```bash
   uvx --from git+https://github.com/github/spec-kit.git specify init --here
   ```
   Choisis l'intégration **Claude Code**, et consulte `specify init --help` si besoin.
2. Crée `docs/speckit-prompts.md` contenant **trois prompts prêts à coller**, chacun dans un bloc de code. Les sections 4.1 à 4.3 ci-dessous décrivent leur contenu.
3. Commit : `docs: backend analysis and speckit prompts`.

### 4.1 Prompt `/speckit.constitution`

Il établit les principes non négociables du front :

- TypeScript strict, aucun `any`.
- **Next.js, dernière version stable, App Router** (vérifie la version actuelle). Server Components par défaut, Client Components uniquement pour l'interactivité.
- **Pattern BFF obligatoire** : le navigateur n'appelle jamais le backend Spring directement. Tous les appels passent par des **Route Handlers** (`app/api/*`) ou des **Server Actions**.
- **JWT uniquement en cookie httpOnly, Secure, SameSite=Lax**, jamais en localStorage. Le Middleware Next.js protège les routes privées.
- Le contrat d'API fait foi : types issus de l'OpenAPI (`openapi-typescript`) ou de `docs/backend/api-map.md`. Les réponses sont validées par **Zod**.
- Variables d'environnement validées au démarrage.
- Tests : **Vitest** + **Testing Library** pour l'unitaire, **Playwright** pour les parcours critiques.
- **WCAG 2.1 AA**. Respect de `prefers-reduced-motion` pour toutes les animations.
- Performance : **LCP < 2,5 s, INP < 200 ms, CLS < 0,1**. Effets lourds chargés en différé ; pause quand l'onglet est inactif ; 60 fps visés.
- Conteneurisation : `output: "standalone"`, Dockerfile multi-stage, prêt pour Kubernetes et Helm.
- Code pédagogique : lisible, commenté sur les choix non évidents.

### 4.2 Prompt `/speckit.specify`

Il décrit uniquement le **QUOI et le POURQUOI**, du point de vue utilisateur, sans technologie. Il doit :

- Se baser **exclusivement** sur les capacités réelles du backend identifiées en phases 2 et 3.
- Pour le premier lot, couvrir : catalogue (liste, filtres, détail), authentification (inscription, connexion, session, déconnexion) et panier (ajout, modification, suppression, persistance multi-appareil).
- Décrire les cas limites : session expirée, stock insuffisant, backend indisponible, utilisateur non connecté qui ajoute au panier.
- Inclure des **critères d'acceptation** testables par user story.
- Lister ce qui est **hors périmètre**.
- Reprendre **intégralement** la section « Direction artistique » ci-dessous.

### 4.3 Prompt `/speckit.plan`

Il décrit le **COMMENT** technique :

- Next.js App Router, TypeScript, **Tailwind CSS** (tokens de la direction artistique dans le thème), **shadcn/ui** comme base de composants.
- **Motion** pour les micro-interactions et transitions, **Lenis** pour le smooth scroll, **View Transitions API** pour liste → détail produit. Canvas 2D ou WebGL léger pour les effets de fond (aurore, grille de points réactive).
- **TanStack Query** pour l'état serveur côté client (panier, mises à jour optimistes).
- Architecture des dossiers (`app/`, `components/`, `features/`, `lib/api/`, `lib/schemas/`, `styles/`).
- Mapping explicite **page → Route Handler/Server Action → endpoint Spring** pour chaque fonctionnalité.
- Stratégie de cache et revalidation (catalogue vs panier).
- Gestion centralisée des erreurs backend.
- Stratégie de refresh du token si le backend le permet.
- Dev local : `docker-compose` lançant front + backend + Postgres + Redis.
- Implémentation de l'interface avec le skill **frontend-design** de Claude Code, en prenant la direction artistique comme référence.

### 4.4 Questions ouvertes

À la fin de `docs/speckit-prompts.md`, liste toutes les `⚠️ À CONFIRMER` et les décisions à prendre, avec ta recommandation pour chacune.

---

## Direction artistique (à inclure dans le prompt specify)

### « Aurora Glow » — sombre, doux à l'œil, premium et vivant

**Intention** : une ambiance nocturne et feutrée, comme une aurore boréale vue de nuit. Les couleurs vivent en arrière-plan et dans les accents, jamais en aplats agressifs. Le doré signe le côté premium. L'œil ne doit jamais fatiguer : pas de blanc pur, pas de noir pur, pas de contrastes violents. **La page doit sembler vivante et dialoguer avec l'utilisateur.**

**Références** : Linear, Stripe, Vercel, Raycast.

**Palette** (tokens CSS / Tailwind) :
| Rôle | Token | Valeur |
|---|---|---|
| Fond principal (bleu nuit) | `--bg` | `#0A0E1F` |
| Surface (cartes) | `--surface` | `#121833` |
| Surface élevée | `--surface-2` | `#1A2142` |
| Bordures | `--border` | `#2A3150` |
| Texte principal | `--text` | `#E6E8F2` |
| Texte secondaire | `--text-muted` | `#8B93B0` |
| Violet (accent principal) | `--violet` | `#8B7CF6` |
| Bleu (accent secondaire) | `--blue` | `#4F7BFF` |
| Or champagne (premium) | `--gold` | `#D4AF6A` |
| Cyan (succès, état actif) | `--cyan` | `#5EEAD4` |
| Rose (alertes douces, rare) | `--rose` | `#F08BB8` |

**Règles d'usage des couleurs** :
- Répartition 60 / 30 / 10 : 60 % bleu nuit, 30 % surfaces et gris, 10 % accents.
- **Doré rare** : prix, badges premium, liseré au survol des cartes. Jamais en fond.
- CTA principal en dégradé violet → bleu avec un léger halo.
- Tous les couples texte / fond respectent WCAG AA.

**Typographie** (polices variables via `next/font`) :
- Titres : **Sora**, 500–600, letter-spacing -0,02em. Alternative éditoriale : **Clash Display** (Fontshare, via `next/font/local`).
- Texte : **Inter**, 400–500, interligne 1,6–1,7.
- Prix et données techniques : **JetBrains Mono**, prix en doré.
- La graisse des titres réagit à la proximité du curseur (axe variable `wght`).

**Composants** :
- Cartes en verre subtil : `--surface` à ~70 % d'opacité, `backdrop-filter: blur(12px)`, bordure 1 px `--border`. Au survol, la bordure devient un dégradé violet → or.
- Rayons de 16 à 20 px. Ombres diffuses teintées de violet, jamais noires.
- Contrôles du panier (quantité, toggles) en dark neumorphism discret, avec un liseré cyan à l'état actif.

**Effet signature : grille de points réactive autour du curseur**
- Le fond porte une grille de petits points très discrète (couleur proche de `--border`, quasi invisible au repos).
- Autour du curseur, dans un rayon d'environ 120 px, les points se révèlent progressivement, grossissent légèrement et sont repoussés vers l'extérieur, formant un anneau vivant.
- Une ombre douce et sombre suit le curseur sous cet anneau, comme si le curseur creusait légèrement la surface.
- Le mouvement est amorti avec de l'inertie : les points reviennent lentement à leur place quand le curseur s'éloigne.
- L'effet est actif uniquement sur les zones de fond, pas au-dessus des cartes ni du texte.
- Sur mobile, il se déclenche au toucher, en version allégée.
- Implémentation en Canvas 2D (ou WebGL si nécessaire), avec un nombre de points adapté à la taille d'écran. Il est désactivé avec `prefers-reduced-motion`.

**Animations** (douces, jamais agressives) :
- Arrière-plan : 2 à 3 blobs aurora (violet, bleu, touche d'or) très floutés, qui dérivent sur des cycles de 20 à 30 s et suivent lentement le curseur.
- Apparition au scroll : fondu + translation de 12 px, 300–400 ms, ease-out, avec stagger.
- Transition partagée carte produit → page détail.
- Skeletons avec un shimmer violet très léger.
- Micro-interactions : 150–250 ms. Aucun clignotement.
- Avec `prefers-reduced-motion` : effets figés, transitions réduites à des fondus.

**Interactivité : « la page dialogue avec l'utilisateur »**
- **Accueil contextuel** : message selon l'heure (« Bonsoir »). Pour un visiteur connu, « Content de te revoir » avec ses derniers produits consultés.
- **Boutons magnétiques** : les CTA principaux sont légèrement attirés par le curseur.
- **Cartes produits** : inclinaison 3D selon le curseur, spotlight interne, reflet doré qui glisse sur la bordure.
- **Titres vivants** : révélation lettre par lettre au premier affichage, graisse réactive au curseur.
- **Recherche instantanée** : palette ⌘K / Ctrl+K avec résultats en direct.
- **Panier vivant** : animation « vole vers le panier », compteur doré qui rebondit, micro-copie chaleureuse, vibration légère sur mobile si supportée.
- **États vides et erreurs** : illustrations animées et textes qui s'adressent à l'utilisateur.
- **Sons d'interface optionnels**, désactivés par défaut.
- **Mobile** : les effets liés au curseur sont remplacés par le toucher et le gyroscope (inclinaison des cartes), avec dégradation propre.
- **Pas d'effets météo ni saisonniers** (pas de neige).

---

## Livrable final

Quand tout est terminé, affiche-moi :
1. Un résumé en 10 lignes maximum de ce que tu as compris du backend.
2. Les 5 points les plus importants à trancher avant de lancer Spec Kit.
3. L'arborescence du repo créé.
4. Les prochaines commandes à lancer, dans l'ordre : `/speckit.constitution`, `/speckit.specify`, `/speckit.plan`, `/speckit.tasks`, `/speckit.implement`. Précise s'il faut redémarrer Claude Code pour que les commandes Spec Kit soient reconnues.

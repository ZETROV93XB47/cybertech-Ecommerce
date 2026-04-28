# Cybertech Keycloak login theme

Custom Keycloak login theme that re-skins the auth pages (`login`, `register`,
`login-reset-password`, `error`) so they match the Material 3 design tokens
used by the Next.js front-end (`front/app/src/app/globals.css`).

## What it overrides

```
src/main/resources/keycloak/themes/cybertech/
└── login/
    ├── theme.properties              # parent=keycloak + register cybertech.css
    ├── template.ftl                  # registrationLayout macro (page shell)
    ├── login.ftl                     # sign-in form
    ├── register.ftl                  # registration form
    ├── login-reset-password.ftl      # forgot-password form
    ├── error.ftl                     # error page
    ├── messages/
    │   ├── messages_en.properties    # selective copy tweaks
    │   └── messages_fr.properties    # FR translations
    └── resources/
        ├── css/cybertech.css         # all visual styling (M3 tokens)
        └── img/logo.svg              # optional logo asset
```

The theme inherits from the bundled `keycloak` parent theme; any file we
don't override (e.g. `login-otp.ftl`, `login-update-password.ftl`, account
console pages, etc.) keeps Keycloak's default rendering.

## How to enable it

1. **Make the theme visible to the container.** It's already mounted via
   `src/main/resources/docker/docker-compose.yml`:

   ```yaml
   keycloak:
     volumes:
       - ./../keycloak/themes/cybertech:/opt/keycloak/themes/cybertech:ro
   ```

   No rebuild needed — Keycloak picks up themes from `/opt/keycloak/themes/`
   on startup.

2. **Restart Keycloak** so it scans the themes directory:

   ```bash
   docker compose -f src/main/resources/docker/docker-compose.yml restart keycloak
   ```

3. **Activate the theme in the realm.** Either:

   - **Via the admin console** ([http://localhost:8080](http://localhost:8080)
     → log in as `admin/admin` → realm `cybertech` → *Realm settings* →
     *Themes* → set **Login theme** to `cybertech` → *Save*), or

   - **Via the realm export** — open `cybertech-realm-export.json` and add
     `"loginTheme": "cybertech"` at the realm root, then re-import.

4. **Verify.** Hit any client login URL (e.g. start the front-end, click
   *Sign in with Keycloak*). You should see the dark CTA button, Inter +
   Space Grotesk fonts and the Cybertech wordmark instead of the default
   Keycloak chrome.

## Local development tips

- Keycloak caches FreeMarker templates aggressively. In `start-dev` mode
  (which is what `docker-compose.yml` uses) the cache is already disabled,
  so a hard refresh is enough. If you ever switch to `start` mode, set
  `KC_SPI_THEME_STATIC_MAX_AGE=-1`, `KC_SPI_THEME_CACHE_THEMES=false`,
  `KC_SPI_THEME_CACHE_TEMPLATES=false`.

- The CSS deliberately mirrors the tokens from `globals.css`. If you
  change a colour there, mirror it in `resources/css/cybertech.css` —
  there is no shared build step between the front-end and Keycloak.

- Fonts come from Google Fonts CDN (Inter + Space Grotesk) so the theme
  needs outbound HTTP from the Keycloak container. For air-gapped setups,
  drop the fonts into `resources/fonts/` and replace the `<link>` in
  `template.ftl` with a local `@font-face` declaration.

## Security note

This theme makes no security changes — it only overrides view templates.
Keep CSP / HSTS / SSL settings on the realm itself. Do not embed secrets
in `cybertech.css` or templates.

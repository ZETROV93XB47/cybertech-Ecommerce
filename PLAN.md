# Plan

- [x] Vérifier le workflow GitHub Actions et les exigences du POM Maven racine.
- [x] Aligner le workflow sur le wrapper Maven et Java 26.
- [x] Valider le wrapper et le build Maven du module racine `com.novatech.cybertech` (tests Surefire; les tests Failsafe ne sont pas activés sans le profil d'intégration).
- [x] Mettre à jour `progress.md` avec le résultat et les prochaines étapes.

## Web Push

- [x] Implémenter l'entité et le dépôt d'abonnements, le service d'inscription/révocation et l'API authentifiée (JPA, repository, service, DTO, controller/spec).
- [x] Implémenter la configuration VAPID, la validation des endpoints Web Push, le processeur push et son intégration au système de notification.
- [x] Déclencher le push de confirmation de paiement et d'expédition uniquement après commit.
- [x] Ajouter les tests unitaires de services/processeur/listeners et tests MVC d'abonnement; exécuter les tests ciblés puis `.\mvnw.cmd -B clean verify` (BUILD SUCCESS, 2243 tests, 0 échec, 0 erreur, 15 ignorés).
- [x] Documenter la configuration front/VAPID requise et mettre à jour `progress.md`.
- [ ] Intégrer le Service Worker et l'interface d'abonnement dans le dépôt frontend, configurer les clés VAPID dans Vault et valider les notifications sur un navigateur réel.
- [ ] Vérifier le diff et l'état Git puis committer uniquement les changements de cette tâche, sans embarquer les fichiers déjà staged/non suivis.

package com.novatech.cybertech.annotation;

import jakarta.persistence.PrePersist;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.UUID;

@Slf4j
public class UUIDGenerator {

    @PrePersist // Méthode appelée avant l'insertion initiale
    public void generateUuid(final Object entity) {
        log.trace("Checking entity {} for @GeneratedUuid fields before persist.", entity.getClass().getSimpleName());
        Field[] fields = entity.getClass().getDeclaredFields(); // Obtient les champs déclarés dans la classe actuelle

        for (Field field : fields) {
            // Vérifie si le champ est annoté avec @GeneratedUuid
            if (field.isAnnotationPresent(GeneratedUUID.class)) {
                // Vérifie si le champ est bien de type UUID
                if (UUID.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true); // Permet d'accéder aux champs privés
                        Object currentValue = field.get(entity); // Récupère la valeur actuelle du champ

                        // Génère un UUID seulement si le champ est actuellement null
                        if (currentValue == null) {
                            UUID newUuid = UUID.randomUUID();
                            field.set(entity, newUuid); // Affecte le nouvel UUID au champ
                            log.debug("Generated UUID {} for field {} in entity {}", newUuid, field.getName(), entity.getClass().getSimpleName());
                        } else {
                            log.trace("Field {} in entity {} already has a UUID value [{}], skipping generation.", field.getName(), entity.getClass().getSimpleName(), currentValue);
                        }
                    } catch (IllegalAccessException e) {
                        log.error("Error accessing field {} in entity {} for UUID generation", field.getName(), entity.getClass().getSimpleName(), e);
                        // Gérer l'erreur comme approprié (peut-être lever une exception runtime)
                    } finally {
                        field.setAccessible(false); // Bonne pratique de restaurer l'accessibilité
                    }
                } else {
                    log.warn("Field {} in entity {} is annotated with @GeneratedUuid but is not of type UUID.", field.getName(), entity.getClass().getSimpleName());
                }
            }
        }
        // Note: Pour gérer les champs hérités, il faudrait remonter la hiérarchie des classes (getSuperclass())
    }
}
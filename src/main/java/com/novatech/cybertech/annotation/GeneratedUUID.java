package com.novatech.cybertech.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour marquer un champ UUID qui doit être
 * automatiquement généré avant la persistance si sa valeur est nulle.
 */
@Target(ElementType.FIELD) // L'annotation s'applique aux champs
@Retention(RetentionPolicy.RUNTIME) // L'annotation doit être disponible au runtime pour la réflexion
public @interface GeneratedUUID {
    // Pas d'attributs nécessaires pour une simple génération de UUID v4
}
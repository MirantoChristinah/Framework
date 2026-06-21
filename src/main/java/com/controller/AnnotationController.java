
package com.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE) // S'applique sur les classes
@Retention(RetentionPolicy.RUNTIME) // Visible à l'exécution via la réflexion
public @annotation AnnotationController {
    // Une annotation simple sans attribut pour le moment
}
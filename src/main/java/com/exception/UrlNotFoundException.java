package com.exception;

public class UrlNotFoundException extends Exception {
    
    public UrlNotFoundException(String urlSaisie) {
        super("Erreur 404 - Framework : Aucun controleur ou methode associe a l'URL '" + urlSaisie + "'");
    }
}
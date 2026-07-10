package com.exception;

public class DuplicateUrlException extends Exception {
    public DuplicateUrlException(String url, String method) {
        super("Erreur Framework : Conflit de routage ! L'URL '" + url + "' avec la methode '" + method + "' est deja associee a une autre fonction.");
    }
}
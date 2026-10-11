package com.exception;

public class BindingException extends Exception {

    public BindingException(String message) {
        super(message);
    }

    public BindingException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getMessageComplet() {
        return "Erreur de Parametre de Binding : " + getMessage();
    }
}
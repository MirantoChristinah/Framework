package com.model;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class MySession {

    private final Map<String, Object> attributs = new LinkedHashMap<>();

    public Object getAttribute(String nom) {
        return attributs.get(nom);
    }

    public void setAttribute(String nom, Object valeur) {
        if (nom == null) {
            throw new IllegalArgumentException("Le nom de l'attribut ne peut pas etre null.");
        }
        if (valeur == null) {
            attributs.remove(nom);
        } else {
            attributs.put(nom, valeur);
        }
    }

    public void removeAttribute(String nom) {
        attributs.remove(nom);
    }

    public boolean containsAttribute(String nom) {
        return attributs.containsKey(nom);
    }

    public Map<String, Object> getAttributs() {
        return attributs;
    }

    /** Copie les attributs de la session HTTP vers l'objet MySession. */
    public void chargerDepuis(HttpSession sessionHttp) {
        attributs.clear();
        Enumeration<String> noms = sessionHttp.getAttributeNames();
        while (noms.hasMoreElements()) {
            String nom = noms.nextElement();
            attributs.put(nom, sessionHttp.getAttribute(nom));
        }
    }

    /** Synchronise ajouts, modifications et suppressions vers HttpSession. */
    public void synchroniserVers(HttpSession sessionHttp) {
        List<String> nomsExistants = Collections.list(sessionHttp.getAttributeNames());
        for (String nom : nomsExistants) {
            if (!attributs.containsKey(nom)) {
                sessionHttp.removeAttribute(nom);
            }
        }
        for (Map.Entry<String, Object> entree : attributs.entrySet()) {
            Object valeur = entree.getValue();
            if (valeur == null) {
                sessionHttp.removeAttribute(entree.getKey());
            } else {
                sessionHttp.setAttribute(entree.getKey(), valeur);
            }
        }
    }
}


package model;

import java.util.HashMap;
import java.util.Map;

public class Session {

    private Map<String, Object> map = new HashMap<>();
    private String sessionId;
    private Object objet;

    public Session() {
    }

    public Session(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getMap() {
        return map;
    }

    public void setMap(Map<String, Object> map) {
        this.map = map;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Object getObjet() {
        return objet;
    }

    public void setObjet(Object objet) {
        this.objet = objet;
    }

    public void ajouter(String nom, Object valeur) {
        map.put(nom, valeur);
    }

    public Object get(String nom) {
        return map.get(nom);
    }

    public void supprimer(String nom) {
        map.remove(nom);
    }

    public boolean contient(String nom) {
        return map.containsKey(nom);
    }
}

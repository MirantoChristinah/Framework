package com.binding;

import java.util.LinkedHashMap;
import java.util.Map;


public class BindingResult {

    private final Object[] arguments;
    private final Map<String, Object> values = new LinkedHashMap<>();
                        
    public BindingResult(Object[] arguments) {
        this.arguments = arguments;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    /** Ajoute une valeur liee (nom du parametre -> valeur). */
    public void add(String nom, Object valeur) {
        values.put(nom, valeur);
    }
}
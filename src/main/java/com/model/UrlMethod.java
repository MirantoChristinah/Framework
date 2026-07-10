package com.model;

import java.util.Objects;

public class UrlMethod {
    private String url;
    private String method; // "GET" ou "POST"

    public UrlMethod(String url, String method) {
        this.url = url;
        this.method = method.toUpperCase(); // On force en majuscules pour éviter les erreurs de casse
    }

    public String getUrl() { return url; }
    public String getMethod() { return method; }

    // Redéfinition essentielle pour la HashMap
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlMethod urlMethod = (UrlMethod) o;
        return Objects.equals(url, urlMethod.url) && Objects.equals(method, urlMethod.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method);
    }
}
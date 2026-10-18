package com.model;

public class MapFile {

    private String nom;
    private String extension;
    private byte[] bytes;

    public MapFile() {
    }

    public MapFile(String nom, String extension, byte[] bytes) {
        this.nom = nom;
        this.extension = extension;
        this.bytes = bytes;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}
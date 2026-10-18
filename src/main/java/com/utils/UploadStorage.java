package com.utils;

import com.exception.BindingException;
import com.model.MapFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.InvalidPathException;
import java.util.Properties;
import java.util.UUID;


public final class UploadStorage {
    private static final Properties CONFIG = new Properties();

    static {
        try (java.io.InputStream in = chargerConfiguration()) {
            if (in != null) {
                CONFIG.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private UploadStorage() {
    }

    private static java.io.InputStream chargerConfiguration() throws IOException {
        java.io.InputStream in = UploadStorage.class.getClassLoader()
                .getResourceAsStream("fichier.properties");
        if (in != null) return in;

        in = UploadStorage.class.getClassLoader()
                .getResourceAsStream("upload.properties");
        if (in != null) return in;

        java.nio.file.Path fichier = java.nio.file.Paths.get("fichier.properties");
        if (java.nio.file.Files.isRegularFile(fichier)) {
            return java.nio.file.Files.newInputStream(fichier);
        }
        return null;
    }

    /**
     * Enregistre le contenu et retourne le chemin absolu sauvegarde.
     * Le nom de stockage est genere par le serveur, pas par le navigateur.
     */
    public static String sauvegarder(MapFile fichier) throws BindingException {
        if (fichier == null || fichier.getBytes() == null || fichier.getBytes().length == 0) {
            throw new BindingException("Aucun fichier a sauvegarder.");
        }

        String dossier = CONFIG.getProperty("upload.directory");
        if (dossier == null || dossier.trim().isEmpty()) {
            throw new BindingException(
                    "La propriete 'upload.directory' est absente de fichier.properties.");
        }
        String extension = fichier.getExtension() == null ? "" : fichier.getExtension().trim();

        // Limiter les caracteres de l'extension avant de l'utiliser dans un chemin.
        extension = extension.replaceAll("[^A-Za-z0-9]", "");
        String nomStockage = UUID.randomUUID().toString()
                + (extension.isEmpty() ? "" : "." + extension);

        try {
            Path repertoire = Paths.get(dossier).toAbsolutePath().normalize();
            Files.createDirectories(repertoire);
            Path destination = repertoire.resolve(nomStockage).normalize();

            if (!destination.getParent().equals(repertoire)) {
                throw new BindingException("Chemin de destination invalide.");
            }

            Files.write(destination, fichier.getBytes(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return destination.toString();

        } catch (IOException | InvalidPathException e) {
            throw new BindingException("Erreur lors de la sauvegarde du fichier.", e);
        }
    }
}
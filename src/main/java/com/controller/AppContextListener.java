package com.controller;


import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@WebListener
public class AppContextListener implements ServletContextListener {

    private Map<UrlMethod, Mapping> mappingUrls = new HashMap<>();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // 1. Récupérer le paramètre d'initialisation (ex: défini dans web.xml ou le contexte)
        String packageToScan = sce.getServletContext().getInitParameter("packageControllers");
        
        if (packageToScan == null || packageToScan.trim().isEmpty()) {
            // Valeur par défaut si non spécifié
            packageToScan = "com.controller"; 
        }

        try {
            // 2. Lancer le scan (la même logique de méthode scanPackage que tu avais)
            scanPackage(packageToScan);
            
            // 3. Sauvegarder la Map dans le contexte partagé
            sce.getServletContext().setAttribute("mesRoutes", mappingUrls);
            System.out.println("[INFO] Scan des packages réussi. Routes enregistrées !");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Échec du scan au démarrage : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // (La méthode scanPackage reste identique à celle de ta Servlet)
    private void scanPackage(String packageName) throws Exception { ... }
}

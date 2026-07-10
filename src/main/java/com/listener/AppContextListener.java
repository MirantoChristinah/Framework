package com.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.HashMap;
import java.util.Map;
import com.model.Mapping;
import com.model.UrlMethod;
import com.utils.RouteLoader;

@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        String packageToScan = sce.getServletContext().getInitParameter("packageControllers");
        if (packageToScan == null || packageToScan.trim().isEmpty()) {
            packageToScan = "com.controller"; 
        }

        try {
            Map<UrlMethod, Mapping> mappingUrls = new HashMap<>();
            
            // On appelle la classe utilitaire externe
            RouteLoader.buildRoutingTable(packageToScan, mappingUrls);
            
            sce.getServletContext().setAttribute("mesRoutes", mappingUrls);
            System.out.println("[INFO] Scan des packages réussi. Routes enregistrées !");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Échec du scan au démarrage : " + e.getMessage());
            e.printStackTrace();
            // Optionnel mais recommandé : bloquer le démarrage de l'application si les routes plantent
            throw new RuntimeException(e); 
        }
    }
}
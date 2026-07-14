package com.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.HashMap;
import java.util.Map;
import com.model.Mapping;
import com.model.UrlMethod;
import com.utils.RouteLoader;
import jakarta.servlet.ServletContext;
@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();

        // 1. Lecture depuis le web.xml
        String packageToScan = context.getInitParameter("packageControllers");
        String viewPrefix = context.getInitParameter("view-prefix");
        String viewSuffix = context.getInitParameter("view-suffix");

        // Valeurs par défaut si le web.xml est mal lu
        if (packageToScan == null) packageToScan = "com.controller";
        if (viewPrefix == null) viewPrefix = "/WEB-INF/Views/";
        if (viewSuffix == null) viewSuffix = ".jsp";

        try {
            Map<UrlMethod, Mapping> mappingUrls = new HashMap<>();
            RouteLoader.buildRoutingTable(packageToScan, mappingUrls);
            
            // 2. STOCKAGE DANS LE CONTEXTE (Indispensable pour le Servlet !)
            context.setAttribute("mesRoutes", mappingUrls);
            context.setAttribute("view-prefix", viewPrefix); // <-- Ne pas oublier !
            context.setAttribute("view-suffix", viewSuffix); // <-- Ne pas oublier !
            
            System.out.println("[INFO] Configuration chargée avec succès !");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
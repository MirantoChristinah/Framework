package com.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.ServletContext;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.annotation.*;
import com.ioc.ApplicationContext;
import com.model.Mapping;
import com.model.UrlMethod;
import com.utils.RouteLoader;

@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();

        String packageToScan = context.getInitParameter("packageControllers");
        String viewPrefix = context.getInitParameter("view-prefix");
        String viewSuffix = context.getInitParameter("view-suffix");

        if (packageToScan == null) packageToScan = "com.controller";
        if (viewPrefix == null) viewPrefix = "/WEB-INF/Views/";
        if (viewSuffix == null) viewSuffix = ".jsp";

        try {
            // -----------------------------------------------------
            // 2. CREATION DU CONTENEUR IOC (UNE SEULE FOIS)
            // -----------------------------------------------------
            ApplicationContext appContext = new ApplicationContext();

            List<Class<?>> allClasses = scanPackage(packageToScan);

            // Enregistrer d'abord les Repository, puis Service, puis Controller
            for (Class<?> cls : allClasses) {
                if (cls.isAnnotationPresent(Repository.class)) {
                    appContext.register(cls);
                    System.out.println("[IOC] Repository enregistre : " + cls.getName());
                }
            }
            for (Class<?> cls : allClasses) {
                if (cls.isAnnotationPresent(Service.class)) {
                    appContext.register(cls);
                    System.out.println("[IOC] Service enregistre : " + cls.getName());
                }
            }
            for (Class<?> cls : allClasses) {
                if (cls.isAnnotationPresent(AnnotationController.class)) {
                    appContext.register(cls);
                    System.out.println("[IOC] Controller enregistre : " + cls.getName());
                }
            }

            context.setAttribute("appContext", appContext);

            // -----------------------------------------------------
            // 3. CONSTRUCTION DES ROUTES
            // -----------------------------------------------------
            Map<UrlMethod, Mapping> mappingUrls = new HashMap<>();
            RouteLoader.buildRoutingTable(packageToScan, mappingUrls);
            context.setAttribute("mesRoutes", mappingUrls);

            // -----------------------------------------------------
            // 4. STOCKAGE DES VUES
            // -----------------------------------------------------
            context.setAttribute("view-prefix", viewPrefix);
            context.setAttribute("view-suffix", viewSuffix);

            System.out.println("[INFO] Conteneur IoC, routes et vues charges avec succes !");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de l'initialisation du framework", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[INFO] Application arretee.");
    }

    // -----------------------------------------------------
    // SCAN RECURSIF
    // -----------------------------------------------------
    private List<Class<?>> scanPackage(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        URL resource = Thread.currentThread().getContextClassLoader().getResource(path);

        if (resource == null) return classes;

        File directory = new File(resource.getFile());
        scanDirectory(directory, packageName, classes);
        return classes;
    }

    private void scanDirectory(File directory, String packageName, List<Class<?>> classes)
            throws ClassNotFoundException {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "."
                        + file.getName().substring(0, file.getName().length() - 6);
                classes.add(Class.forName(className));
            }
        }
    }
}

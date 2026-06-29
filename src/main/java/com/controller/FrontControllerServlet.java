package com.controller;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;

public class FrontControllerServlet extends HttpServlet {

    // Table de hachage associant une URL saisie à son Mapping (Contrôleur + Méthode)
    private HashMap<String, Mapping> mappingUrls = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        
        // 1. Récupérer le paramètre du web.xml
        String packageToScan = config.getInitParameter("packageControllers");
        
        if (packageToScan != null && !packageToScan.trim().isEmpty()) {
            try {
                // 2. Parcourir et scanner le package racine
                scanPackage(packageToScan);
            } catch (Exception e) {
                throw new ServletException("Erreur lors du scan du package : " + packageToScan, e);
            }
        }
    }

    private void scanPackage(String packageName) throws ClassNotFoundException {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            return;
        }

        File directory = new File(resource.getFile());
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    // Si c'est un sous-dossier, on descend dedans récursivement
                    if (file.isDirectory()) {
                        scanPackage(packageName + "." + file.getName());
                    } 
                    // Si c'est un fichier .class, on l'analyse
                    else if (file.getName().endsWith(".class")) {
                        String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                        Class<?> cls = Class.forName(className);

                        // On vérifie si la classe possède l'annotation @AnnotationController
                        if (cls.isAnnotationPresent(AnnotationController.class)) {
                            
                            // On inspecte toutes les méthodes de cette classe
                            Method[] methods = cls.getDeclaredMethods();
                            for (Method method : methods) {
                                
                                // Si la méthode possède l'annotation @UrlMapping
                                if (method.isAnnotationPresent(UrlMapping.class)) {
                                    UrlMapping urlMapping = method.getAnnotation(UrlMapping.class);
                                    String urlValue = urlMapping.value(); // Récupère la valeur via value()

                                    // On crée le Mapping et on l'enregistre dans la Map
                                    Mapping mapping = new Mapping(cls.getName(), method.getName());
                                    mappingUrls.put(urlValue, mapping);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String urlSaisie = requestURI.substring(contextPath.length());
        
        // 3. Recherche du mapping associé à l'URL demandée
        Mapping matchMapping = mappingUrls.get(urlSaisie);

        // Si l'URL demandée n'existe pas, on lève notre propre exception personnalisée
        if (matchMapping == null) {
            try {
                throw new UrlNotFoundException(urlSaisie);
            } catch (UrlNotFoundException e) {
                // On encapsule l'exception dans une ServletException pour que Tomcat l'affiche proprement à l'écran
                throw new ServletException(e.getMessage(), e);
            }
        }

        // Si trouvée, on affiche les informations de mapping pour confirmer le succès du Sprint 2
        try (PrintWriter out = response.getWriter()) {
            out.println("<h1>Framework Test - Sprint 2</h1>");
            out.println("<p>URL saisie detectee : <strong>" + urlSaisie + "</strong></p>");
            
            out.println("<div style='border: 2px solid green; background-color: #f4fff4; padding: 15px; margin-top: 20px; border-radius: 5px;'>");
            out.println("<h3 style='color: green; margin-top: 0;'>[OK] URL Mapping Trouve !</h3>");
            out.println("<p><strong>Classe de destination :</strong> " + matchMapping.getClassName() + "</p>");
            out.println("<p><strong>Methode cible :</strong> " + matchMapping.getMethod() + "()</p>");
            out.println("</div>");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
}
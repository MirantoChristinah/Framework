package com.controller;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FrontControllerServlet extends HttpServlet {

    // Liste demandée pour stocker les noms des contrôleurs détectés
    private List<String> listController = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        
        // 1. Récupérer le paramètre du web.xml
        String packageToScan = config.getInitParameter("packageControllers");
        
        if (packageToScan != null && !packageToScan.trim().isEmpty()) {
            try {
                // 2. Parcourir et scanner le package
                scanPackage(packageToScan);
            } catch (Exception e) {
                throw new ServletException("Erreur lors du scan du package : " + packageToScan, e);
            }
        }
    }

    private void scanPackage(String packageName) throws ClassNotFoundException {
        // Remplacer les "." par "/" pour obtenir le chemin d'accès au dossier
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
                    // On ne prend que les fichiers .class
                    if (file.getName().endsWith(".class")) {
                        String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                        Class<?> cls = Class.forName(className);

                        // 3. VÉRIFICATION CORRIGÉE : Utilisation de AnnotationController.class
                        if (cls.isAnnotationPresent(AnnotationController.class)) {
                            listController.add(cls.getName());
                        }
                    }
                }
            }
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            String requestURI = request.getRequestURI();
            String contextPath = request.getContextPath();
            String urlSaisie = requestURI.substring(contextPath.length());

            out.println("<h1>Framework Test - Sprint 1</h1>");
            out.println("<p>URL saisie detectee : <strong>" + urlSaisie + "</strong></p>");
            
            // Affichage des contrôleurs détectés pour le debug du Sprint 1
            out.println("<h3>Contrôleurs détectés avec @AnnotationController :</h3>");
            out.println("<ul>");
            for (String controller : listController) {
                out.println("<li>" + controller + "</li>");
            }
            out.println("</ul>");
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
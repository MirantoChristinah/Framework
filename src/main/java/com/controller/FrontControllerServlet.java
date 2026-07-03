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
import java.util.Map;

public class FrontControllerServlet extends HttpServlet {

    // Table de hachage associant un couple (URL, Méthode HTTP) à son Mapping
    private Map<UrlMethod, Mapping> mappingUrls = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        String packageToScan = config.getInitParameter("packageControllers");
        
        if (packageToScan != null && !packageToScan.trim().isEmpty()) {
            try {
                scanPackage(packageToScan);
            } catch (Exception e) {
                throw new ServletException("Erreur lors du scan du package : " + packageToScan, e);
            }
        }
    }

    private void scanPackage(String packageName) throws ClassNotFoundException, DuplicateUrlException {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) return;

        File directory = new File(resource.getFile());
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        scanPackage(packageName + "." + file.getName());
                    } else if (file.getName().endsWith(".class")) {
                        String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                        Class<?> cls = Class.forName(className);

                        if (cls.isAnnotationPresent(AnnotationController.class)) {
                            Method[] methods = cls.getDeclaredMethods();
                            for (Method method : methods) {
                                
                                if (method.isAnnotationPresent(UrlMapping.class)) {
                                    UrlMapping urlMapping = method.getAnnotation(UrlMapping.class);
                                    
                                    String urlValue = urlMapping.value();
                                    String httpMethod = urlMapping.method().toUpperCase();

                                    // Création de l'instance UrlMethod (Clé)
                                    UrlMethod urlMethodKey = new UrlMethod(urlValue, httpMethod);

                                    // Sprint 3 : Exception si la fonction a la même méthode et même URL
                                    if (mappingUrls.containsKey(urlMethodKey)) {
                                        throw new DuplicateUrlException(urlValue, httpMethod);
                                    }

                                    // Stockage dans la Map
                                    Mapping mapping = new Mapping(cls.getName(), method.getName());
                                    mappingUrls.put(urlMethodKey, mapping);
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
        String methodeAppelee = request.getMethod().toUpperCase(); 

        // Sécurité : Si c'est un forward interne vers WEB-INF suite à l'exécution d'une méthode,
        // on laisse Tomcat servir le fichier HTML de manière standard.
        if (urlSaisie.startsWith("/WEB-INF/")) {
            getServletContext().getNamedDispatcher("default").forward(request, response);
            return;
        }

        // Recherche du mapping avec le couple (URL, Méthode)
        UrlMethod rechercheKey = new UrlMethod(urlSaisie, methodeAppelee);
        Mapping matchMapping = mappingUrls.get(rechercheKey);

        // Si l'URL et la méthode ne correspondent à rien
        if (matchMapping == null) {
            try {
                throw new UrlNotFoundException(urlSaisie + " [" + methodeAppelee + "]");
            } catch (UrlNotFoundException e) {
                throw new ServletException(e.getMessage(), e);
            }
        }

        // EXÉCUTION DYNAMIQUE DU CONTRÔLEUR VIA RÉFLEXION
        try {
            Class<?> targetClass = Class.forName(matchMapping.getClassName());
            Object controllerInstance = targetClass.getDeclaredConstructor().newInstance();
            
            try {
                // Étape A : On cherche d'abord si la méthode attend (HttpServletRequest, HttpServletResponse)
                Method methodToExecute = targetClass.getMethod(matchMapping.getMethod(), 
                        HttpServletRequest.class, HttpServletResponse.class);
                
                // Exécution (Utile pour votre méthode showForm qui fait le forward)
                methodToExecute.invoke(controllerInstance, request, response);
                
            } catch (NoSuchMethodException e) {
                // Étape B : Si la méthode n'a pas d'arguments, on l'appelle à vide
                Method methodToExecute = targetClass.getMethod(matchMapping.getMethod());
                methodToExecute.invoke(controllerInstance);
                
                // On affiche un retour visuel pour confirmer l'exécution de la méthode vide
                try (PrintWriter out = response.getWriter()) {
                    out.println("<h1>Framework Test - Sprint 3</h1>");
                    out.println("<p>URL saisie détectée : <strong>" + urlSaisie + "</strong></p>");
                    out.println("<p>Méthode HTTP détectée : <strong>" + methodeAppelee + "</strong></p>");
                    
                    out.println("<div style='border: 2px solid green; background-color: #f4fff4; padding: 15px; margin-top: 20px; border-radius: 5px;'>");
                    out.println("<h3 style='color: green; margin-top: 0;'>[OK] Méthode vide exécutée avec succès !</h3>");
                    out.println("<p><strong>Classe :</strong> " + matchMapping.getClassName() + "</p>");
                    out.println("<p><strong>Fonction :</strong> " + matchMapping.getMethod() + "()</p>");
                    out.println("</div>");
                }
            }

        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'exécution du contrôleur : " + matchMapping.getClassName(), e);
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
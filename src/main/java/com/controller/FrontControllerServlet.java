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

    // Changement ici : La clé est maintenant un objet UrlMethod
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
                                    
                                    // 1. On extrait l'URL et la méthode HTTP (GET/POST)
                                    String urlValue = urlMapping.value();
                                    String httpMethod = urlMapping.method().toUpperCase();

                                    // 2. On instancie la clé UrlMethod
                                    UrlMethod urlMethodKey = new UrlMethod(urlValue, httpMethod);

                                    // 3. VÉRIFICATION DU DOUBLON (Grâce à equals et hashCode de UrlMethod)
                                    if (mappingUrls.containsKey(urlMethodKey)) {
                                        throw new DuplicateUrlException(urlValue, httpMethod);
                                    }

                                    // 4. Stockage si tout est OK
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
    String methodeAppelee = request.getMethod(); 

    UrlMethod rechercheKey = new UrlMethod(urlSaisie, methodeAppelee);
    Mapping matchMapping = mappingUrls.get(rechercheKey);

    if (matchMapping == null) {
        try {
            throw new UrlNotFoundException(urlSaisie + " [" + methodeAppelee + "]");
        } catch (UrlNotFoundException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    // --- DEBUT DE L'INVOCATION DYNAMIQUE ---
    Object resultatMethode = null;
    try {
        // 1. Charger la classe du contrôleur
        Class<?> cls = Class.forName(matchMapping.getClassName());

        // 2. Créer l'instance du contrôleur
        Object controleurInstance = cls.getDeclaredConstructor().newInstance();

        // 3. Récupérer la méthode
        Method methodeAExecuter = cls.getDeclaredMethod(matchMapping.getMethod());

        // 4. Invoquer la méthode et récupérer le résultat
        resultatMethode = methodeAExecuter.invoke(controleurInstance);

        // 5. Vérification dans la console du serveur
        System.out.println("[SUCCESS] Méthode appelée avec succès : " 
                + matchMapping.getClassName() + "." + matchMapping.getMethod() + "()");
        System.out.println("[INFO] Résultat renvoyé par la méthode : " + resultatMethode);

    } catch (Exception e) {
        System.out.println("[ERROR] Échec de l'appel de la méthode : " + e.getMessage());
        e.printStackTrace();
        throw new ServletException("Erreur d'invocation du contrôleur", e);
    }
    // --- FIN DE L'INVOCATION DYNAMIQUE ---

    try (PrintWriter out = response.getWriter()) {
        out.println("<h1>Framework Test - Sprint 3</h1>");
        out.println("<p>URL saisie détectée : <strong>" + urlSaisie + "</strong></p>");
        out.println("<p>Méthode HTTP détectée : <strong>" + methodeAppelee + "</strong></p>");
        
        out.println("<div style='border: 2px solid blue; background-color: #f0f4ff; padding: 15px; margin-top: 20px; border-radius: 5px;'>");
        out.println("<h3 style='color: blue; margin-top: 0;'>[Sprint 3] Route trouvée et exécutée !</h3>");
        out.println("<p><strong>Contrôleur cible :</strong> " + matchMapping.getClassName() + "</p>");
        out.println("<p><strong>Méthode exécutée :</strong> " + matchMapping.getMethod() + "()</p>");
        out.println("<p><strong>Retour de la méthode :</strong> " + resultatMethode + "</p>");
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
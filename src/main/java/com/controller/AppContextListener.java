package com.controller;


import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.File;
import java.lang.reflect.Method;
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

}

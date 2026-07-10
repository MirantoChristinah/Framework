package com.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import com.exception.UrlNotFoundException;
import com.model.Mapping;
import com.model.UrlMethod;

public class FrontControllerServlet extends HttpServlet {

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        // 1. 📬 Récupération de la Map partagée depuis le ServletContext
        Map<UrlMethod, Mapping> mappingUrls = (Map<UrlMethod, Mapping>) getServletContext().getAttribute("mesRoutes");
        
        // Sécurité si le listener n'a pas pu s'exécuter correctement
        if (mappingUrls == null) {
            throw new ServletException("Le registre des routes n'a pas été initialisé au démarrage.");
        }

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

        // --- INVOCATION DYNAMIQUE ---
        Object resultatMethode = null;
        try {
            Class<?> cls = Class.forName(matchMapping.getClassName());
            Object controleurInstance = cls.getDeclaredConstructor().newInstance();
            Method methodeAExecuter = cls.getDeclaredMethod(matchMapping.getMethod());
            
            // Exécution
            resultatMethode = methodeAExecuter.invoke(controleurInstance);

            // 💻 Validation dans la console du serveur
            System.out.println("[SUCCESS] URL demandée : " + urlSaisie + " [" + methodeAppelee + "]");
            System.out.println("[SUCCESS] Méthode exécutée : " + matchMapping.getClassName() + "." + matchMapping.getMethod() + "()");
            System.out.println("[INFO] Résultat renvoyé par la méthode du contrôleur : " + resultatMethode);

        } catch (Exception e) {
            System.out.println("[ERROR] Échec de l'appel : " + e.getMessage());
            e.printStackTrace();
            throw new ServletException("Erreur d'invocation du contrôleur", e);
        }

        // --- AFFICHAGE DE LA RÉPONSE ---
        try (PrintWriter out = response.getWriter()) {
            out.println("<h1>Framework Test - Sprint 3 (Avec Listener)</h1>");
            out.println("<p>URL saisie détectée : <strong>" + urlSaisie + "</strong></p>");
            out.println("<p>Méthode HTTP détectée : <strong>" + methodeAppelee + "</strong></p>");
            
            out.println("<div style='border: 2px solid green; background-color: #f4fff4; padding: 15px; margin-top: 20px; border-radius: 5px;'>");
            out.println("<h3 style='color: green; margin-top: 0;'>[Sprint 3] Route trouvée et exécutée via le Listener !</h3>");
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
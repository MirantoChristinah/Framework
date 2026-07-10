package com.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import com.model.Mapping;
import com.model.ModelView;
import com.model.UrlMethod;

public class FrontControllerServlet extends HttpServlet {

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        // 1. 📬 Récupération de la Map partagée depuis le ServletContext
        // Attention : utilise la même clé que dans ton Listener ("mesRoutes")
        Map<UrlMethod, Mapping> mappingUrls = (Map<UrlMethod, Mapping>) getServletContext().getAttribute("mesRoutes");
        
        // Sécurité si le listener n'a pas pu s'exécuter correctement au démarrage
        if (mappingUrls == null) {
            throw new ServletException("Le registre des routes n'a pas été initialisé au démarrage.");
        }

        PrintWriter out = response.getWriter();
        String urlMain = request.getRequestURL().toString();
        String contextPath = request.getContextPath();
        String url = request.getRequestURI().substring(contextPath.length());
                        
        // Récupération des préfixes/suffixes de vue (si tu les gères via le listener)
        String viewPrefix = (String) getServletContext().getAttribute("view-prefix");
        String viewSuffix = (String) getServletContext().getAttribute("view-suffix");
        
        // Valeurs par défaut si non définies dans le contexte
        if (viewPrefix == null) viewPrefix = "/WEB-INF/views/";
        if (viewSuffix == null) viewSuffix = ".jsp";

        String reqMethod = request.getMethod();
        UrlMethod urlMethod = new UrlMethod(url, reqMethod);

        // 2. 🚦 Vérification de l'existence de la route (URL + Méthode HTTP)
        if (mappingUrls.containsKey(urlMethod)) {
            try {
                Mapping mapping = mappingUrls.get(urlMethod); 
                
                // A. Récupération et instanciation de la classe du contrôleur
                // Note : Si ton mapping stocke un String (ex: "com.controller.SakaizController"), on utilise Class.forName()
                Class<?> controllerClass = Class.forName(mapping.getClassName());
                Object controller = controllerClass.getDeclaredConstructor().newInstance();

                // B. Récupération de la méthode grâce à son nom (String) via la réflexion
                String methodName = mapping.getMethod();
                Method method = controllerClass.getDeclaredMethod(methodName);

                // C. Invocation de la méthode sur l'instance du contrôleur
                Object result = method.invoke(controller);

                // D. Traitement du résultat si c'est un ModelView
                if (result instanceof ModelView) {
                    ModelView mv = (ModelView) result;

                    // On injecte les données du ModelView dans les attributs de la requête
                    if (mv.getData() != null) {
                        for (Map.Entry<String, Object> e : mv.getData().entrySet()) {
                            request.setAttribute(e.getKey(), e.getValue());
                        }
                    }

                    // Construction du chemin de la vue
                    String view = viewPrefix + mv.getUrl() + viewSuffix;

                    // Redirection interne (Forward) vers la vue
                    request.getRequestDispatcher(view).forward(request, response);
                    return;
                } else {
                    // Si la méthode ne retourne pas un ModelView (ex: String, void, etc.)
                    out.println("<h2>FrontController servlet</h2>");
                    out.println("<p><strong>Current URL:</strong> " + urlMain + "</p>");
                    out.println("<p>La méthode a retourné : " + result + "</p>");
                }
                
            } catch (Exception e) {
                // On encapsule l'exception réelle pour avoir un affichage précis de l'erreur dans la console
                throw new ServletException("Erreur lors de l'exécution du contrôleur pour l'URL: " + url, e);
            }
        } else {
            // 404 personnalisé si aucune route ne correspond
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Aucune route trouvée pour l'URL : " + url + " [" + reqMethod + "]");
            return;
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
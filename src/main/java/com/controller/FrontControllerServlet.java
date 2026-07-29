package com.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import com.ioc.ApplicationContext;
import com.model.Mapping;
import com.model.ModelView;
import com.model.UrlMethod;

@WebServlet(name = "FrontController", urlPatterns = {"/"})
public class FrontControllerServlet extends HttpServlet {

    private String viewPrefix;
    private String viewSuffix;
    private ApplicationContext appContext; // ← LE CONTENEUR IOC (singleton)

    @Override
    public void init() throws ServletException {
        ServletContext ctx = getServletContext();

        // Récupération du conteneur (créé UNE SEULE FOIS dans le Listener)
        this.appContext = (ApplicationContext) ctx.getAttribute("appContext");
        if (this.appContext == null) {
            throw new ServletException("Le conteneur IoC n'a pas été initialisé au démarrage.");
        }

        // Récupération des vues (via getAttribute, pas getInitParameter)
        this.viewPrefix = (String) ctx.getAttribute("view-prefix");
        this.viewSuffix = (String) ctx.getAttribute("view-suffix");

        if (viewPrefix == null) viewPrefix = "/WEB-INF/Views/";
        if (viewSuffix == null) viewSuffix = ".jsp";
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        // 1. Récupération des routes depuis le contexte
        @SuppressWarnings("unchecked")
        Map<UrlMethod, Mapping> mappingUrls =
                (Map<UrlMethod, Mapping>) getServletContext().getAttribute("mesRoutes");

        if (mappingUrls == null) {
            throw new ServletException("Le registre des routes n'a pas été initialisé au démarrage.");
        }

        // 2. Parsing de l'URL
        String urlMain = request.getRequestURL().toString();
        String contextPath = request.getContextPath();
        String url = request.getRequestURI().substring(contextPath.length());
        String reqMethod = request.getMethod();
        UrlMethod urlMethod = new UrlMethod(url, reqMethod);

        // 3. Traitement de la route
        if (mappingUrls.containsKey(urlMethod)) {
            try {
                Mapping mapping = mappingUrls.get(urlMethod);
                Class<?> controllerClass = Class.forName(mapping.getClassName());

                // ← CLÉ : récupère l'instance depuis le CONTENEUR (SINGLETON)
                // au lieu de newInstance() à chaque requête (PROTOTYPE)
                Object controller = appContext.getBean(controllerClass);
                if (controller == null) {
                    throw new ServletException("Controller non trouvé dans le conteneur : " + controllerClass.getName());
                }

                // Récupération de la méthode
                String methodName = mapping.getMethod();
                Method method = controllerClass.getDeclaredMethod(methodName);

                // Invocation (le controller a déjà ses @Autowired injectés)
                Object result = method.invoke(controller);

                // Traitement ModelView
                if (result instanceof ModelView) {
                    ModelView mv = (ModelView) result;

                    if (mv.getData() != null) {
                        for (Map.Entry<String, Object> e : mv.getData().entrySet()) {
                            request.setAttribute(e.getKey(), e.getValue());
                        }
                    }

                    String view = viewPrefix + mv.getUrl() + viewSuffix;
                    request.getRequestDispatcher(view).forward(request, response);
                    return;

                } else {
                    // Résultat brut (String, int, etc.)
                    response.setContentType("text/html;charset=UTF-8");
                    try (PrintWriter out = response.getWriter()) {
                        out.println("<h2>FrontController servlet</h2>");
                        out.println("<p><strong>Current URL:</strong> " + urlMain + "</p>");
                        out.println("<p>La méthode a retourné : " + result + "</p>");
                    }
                }

            } catch (Exception e) {
                throw new ServletException("Erreur lors de l'exécution du contrôleur pour l'URL: " + url, e);
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Aucune route trouvée pour l'URL : " + url + " [" + reqMethod + "]");
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
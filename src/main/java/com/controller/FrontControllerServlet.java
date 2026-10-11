package com.controller;

import jakarta.servlet.ServletContext;
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
import com.model.JsonResult;
import com.model.UrlMethod;
import com.utils.JsonConvertir;
import com.binding.ParameterBinder;
import com.binding.BindingResult;

@WebServlet(name = "FrontController", urlPatterns = {"/"})
public class FrontControllerServlet extends HttpServlet {

    private String viewPrefix;
    private String viewSuffix;
    private ApplicationContext appContext; // LE CONTENEUR IOC (singleton)

    @Override
    public void init() throws ServletException {
        ServletContext ctx = getServletContext();

        // Recuperation du conteneur (cree UNE SEULE FOIS dans le Listener)
        this.appContext = (ApplicationContext) ctx.getAttribute("appContext");
        if (this.appContext == null) {
            throw new ServletException("Le conteneur IoC n'a pas ete initialise au demarrage.");
        }

        // Recuperation des vues
        this.viewPrefix = (String) ctx.getAttribute("view-prefix");
        this.viewSuffix = (String) ctx.getAttribute("view-suffix");

        if (viewPrefix == null) viewPrefix = "/WEB-INF/Views/";
        if (viewSuffix == null) viewSuffix = ".jsp";
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        // 1. Recuperation des routes depuis le contexte
        @SuppressWarnings("unchecked")
        Map<UrlMethod, Mapping> mappingUrls =
                (Map<UrlMethod, Mapping>) getServletContext().getAttribute("mesRoutes");

        if (mappingUrls == null) {
            throw new ServletException("Le registre des routes n'a pas ete initialise au demarrage.");
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

                // Recuperation du bean de controleur depuis le conteneur IoC
                Object controller = appContext.getBean(controllerClass);
                if (controller == null) {
                    throw new ServletException("Controller non trouve dans le conteneur : " + controllerClass.getName());
                }

                // Trouve la methode dans la classe par son nom
                String methodName = mapping.getMethod();
                Method method = chercherMethodeParNom(controllerClass, methodName);

                if (method == null) {
                    throw new ServletException("Methode non trouvee : " + methodName + " dans " + controllerClass.getName());
                }

                // Verification de l'accessibilite
                if (!method.canAccess(controller)) {
                    throw new ServletException("Acces a la methode refuse : " + controllerClass.getName() + "." + methodName);
                }

                // GESTION DES PARAMETRES ET BINDING 
                Object result;
                
                if (method.getParameterCount() > 0) {
                    // Si la methode a des parametres -> On invoque le ParameterBinder
                    BindingResult binding = ParameterBinder.bind(method, request, response);

                    // Les valeurs liees sont mises en attributs de requete pour les vues JSP
                    if (binding != null && binding.getValues() != null) {
                        for (Map.Entry<String, Object> e : binding.getValues().entrySet()) {
                            request.setAttribute(e.getKey(), e.getValue());
                        }
                    }

                    // Invocation avec le tableau d'arguments extrait par le binder
                    Object[] args = (binding != null) ? binding.getArguments() : new Object[method.getParameterCount()];
                    result = method.invoke(controller, args);

                } else {
                    // Aucun parametre -> invocation directe avec null / sans arguments
                    result = method.invoke(controller);
                }

                // WEB API (JSON) : un JsonResult demande explicitement une reponse JSON.
                // Aucune annotation n'est necessaire sur la methode ou le controleur.
                if (result instanceof JsonResult) {
                    JsonResult jsonResult = (JsonResult) result;

                    response.setContentType("application/json;charset=UTF-8");

                    try (PrintWriter out = response.getWriter()) {
                        out.print(JsonConvertir.toJson(jsonResult.getData()));
                    }
                    return;
                }

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
                    // Resultat brut (String, int, etc.)
                    response.setContentType("text/html;charset=UTF-8");
                    try (PrintWriter out = response.getWriter()) {
                        out.println("<h2>FrontController servlet</h2>");
                        out.println("<p><strong>Current URL:</strong> " + urlMain + "</p>");
                        out.println("<p>La methode a retourne : " + result + "</p>");
                    }
                }

            } catch (Exception e) {
                throw new ServletException("Erreur lors de l'execution du controleur pour l'URL: " + url, e);
            }

        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Aucune route trouvee pour l'URL : " + url + " [" + reqMethod + "]");
        }
    }

    //Recherche une methode par son nom dans la classe sans se soucier du nombre ni du type de ses parametres.
    private Method chercherMethodeParNom(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
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

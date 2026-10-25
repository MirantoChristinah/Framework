
package com.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ioc.ApplicationContext;
import com.model.Mapping;
import com.model.ModelView;
import com.model.JsonResult;
import com.model.UrlMethod;
import com.utils.JsonConvertir;
import com.annotation.JsonAnnotation;
import com.binding.ParameterBinder;
import com.binding.BindingResult;

import model.Session;

@WebServlet(name = "FrontController", urlPatterns = {"/"})
@MultipartConfig
public class FrontControllerServlet extends HttpServlet {

    private String viewPrefix;
    private String viewSuffix;
    private ApplicationContext appContext;

    @Override
    public void init() throws ServletException {
        ServletContext ctx = getServletContext();

        this.appContext =
                (ApplicationContext) ctx.getAttribute("appContext");

        if (this.appContext == null) {
            throw new ServletException(
                    "Le conteneur IoC n'a pas ete initialise au demarrage.");
        }

        this.viewPrefix = (String) ctx.getAttribute("view-prefix");
        this.viewSuffix = (String) ctx.getAttribute("view-suffix");

        if (viewPrefix == null) {
            viewPrefix = "/WEB-INF/Views/";
        }

        if (viewSuffix == null) {
            viewSuffix = ".jsp";
        }
    }

    protected void processRequest(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        @SuppressWarnings("unchecked")
        Map<UrlMethod, Mapping> mappingUrls =
                (Map<UrlMethod, Mapping>) getServletContext()
                        .getAttribute("mesRoutes");

        if (mappingUrls == null) {
            throw new ServletException(
                    "Le registre des routes n'a pas ete initialise au demarrage.");
        }

        String urlMain = request.getRequestURL().toString();
        String contextPath = request.getContextPath();
        String url = request.getRequestURI()
                .substring(contextPath.length());
        String reqMethod = request.getMethod();

        UrlMethod urlMethod = new UrlMethod(url, reqMethod);

        if (!mappingUrls.containsKey(urlMethod)) {
            response.sendError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "Aucune route trouvee pour l'URL : "
                            + url + " [" + reqMethod + "]");
            return;
        }

        try {
            Mapping mapping = mappingUrls.get(urlMethod);

            Class<?> controllerClass =
                    Class.forName(mapping.getClassName());

            // Le conteneur IoC fournit l'instance partagee du controleur.
            Object controller = appContext.getBean(controllerClass);

            if (controller == null) {
                throw new ServletException(
                        "Controller non trouve dans le conteneur : "
                                + controllerClass.getName());
            }

            String methodName = mapping.getMethod();

            Method method =
                    chercherMethodeParNom(controllerClass, methodName);

            if (method == null) {
                throw new ServletException(
                        "Methode non trouvee : " + methodName
                                + " dans " + controllerClass.getName());
            }

            if (!method.canAccess(controller)) {
                throw new ServletException(
                        "Acces a la methode refuse : "
                                + controllerClass.getName()
                                + "." + methodName);
            }

            // -------------------------------------------------
            // 1. PREPARATION DES PARAMETRES
            // -------------------------------------------------

            Object[] args = new Object[method.getParameterCount()];

            boolean utiliseSession = false;

            for (Class<?> type : method.getParameterTypes()) {
                if (type == Session.class) {
                    utiliseSession = true;
                    break;
                }
            }

            Session session = null;
            HttpSession httpSession = null;
            Set<String> anciennesCles = new HashSet<>();

            if (utiliseSession) {

                // Une session HTTP propre au client courant.
                httpSession = request.getSession(true);

                // Copier les attributs HTTP dans notre objet Session.
                session = new Session(httpSession.getId());

                Enumeration<String> noms =
                        httpSession.getAttributeNames();

                while (noms.hasMoreElements()) {
                    String nom = noms.nextElement();

                    anciennesCles.add(nom);

                    Object valeur = httpSession.getAttribute(nom);

                    session.ajouter(nom, valeur);
                }
            }

            // -------------------------------------------------
            // 2. BINDING DES PARAMETRES EXISTANTS
            // -------------------------------------------------

            if (method.getParameterCount() > 0) {

              
                BindingResult binding =
                        ParameterBinder.bind(method, request, response);

                if (binding != null && binding.getValues() != null) {
                    for (Map.Entry<String, Object> e
                            : binding.getValues().entrySet()) {

                        request.setAttribute(e.getKey(), e.getValue());
                    }
                }

                if (binding != null && binding.getArguments() != null) {
                    args = binding.getArguments();
                }

                // Injection de la meme instance Session
                // dans chaque parametre Session de cette action.
                Class<?>[] types = method.getParameterTypes();

                for (int i = 0; i < types.length; i++) {
                    if (types[i] == Session.class) {
                        args[i] = session;
                    }
                }
            }

            // -------------------------------------------------
            // 3. INVOCATION DE LA METHODE D'ACTION
            // -------------------------------------------------

            Object result = method.invoke(controller, args);

            // -------------------------------------------------
            // 4. SYNCHRONISATION APRES L'ACTION
            // -------------------------------------------------

            if (utiliseSession && session != null) {

                Map<String, Object> variables = session.getMap();

                // Supprimer de HttpSession les cles retirees
                // de la Map par le developpeur.
                for (String ancienneCle : anciennesCles) {
                    if (!variables.containsKey(ancienneCle)) {
                        httpSession.removeAttribute(ancienneCle);
                    }
                }

                // Ajouter ou mettre a jour les attributs.
                for (Map.Entry<String, Object> entree
                        : variables.entrySet()) {

                    String nom = entree.getKey();
                    Object valeur = entree.getValue();

                    if (valeur == null) {
                        httpSession.removeAttribute(nom);
                    } else {
                        httpSession.setAttribute(nom, valeur);
                    }
                }
            }

            // -------------------------------------------------
            // 5. TRAITEMENT JSON EXPLICITE
            // -------------------------------------------------

            if (result instanceof JsonResult) {
                JsonResult jsonResult = (JsonResult) result;

                response.setContentType("application/json;charset=UTF-8");

                try (PrintWriter out = response.getWriter()) {
                    out.print(JsonConvertir.toJson(jsonResult.getData()));
                }

                return;
            }

            // -------------------------------------------------
            // 6. COMPATIBILITE AVEC JsonAnnotation
            // -------------------------------------------------

            if (method.isAnnotationPresent(JsonAnnotation.class)
                    || controllerClass.isAnnotationPresent(
                            JsonAnnotation.class)) {

                response.setContentType("application/json;charset=UTF-8");

                try (PrintWriter out = response.getWriter()) {
                    out.print(JsonConvertir.toJson(result));
                }

                return;
            }

            // -------------------------------------------------
            // 7. TRAITEMENT ModelView
            // -------------------------------------------------

            if (result instanceof ModelView) {
                ModelView mv = (ModelView) result;

                if (mv.getData() != null) {
                    for (Map.Entry<String, Object> e
                            : mv.getData().entrySet()) {

                        request.setAttribute(e.getKey(), e.getValue());
                    }
                }

                String view = viewPrefix + mv.getUrl() + viewSuffix;

                request.getRequestDispatcher(view)
                        .forward(request, response);

                return;
            }

            // -------------------------------------------------
            // 8. RESULTAT BRUT
            // -------------------------------------------------

            response.setContentType("text/html;charset=UTF-8");

            try (PrintWriter out = response.getWriter()) {
                out.println("<h2>FrontController servlet</h2>");
                out.println("<p><strong>Current URL:</strong> "
                        + urlMain + "</p>");
                out.println("<p>La methode a retourne : "
                        + result + "</p>");
            }

        } catch (Exception e) {
            throw new ServletException(
                    "Erreur lors de l'execution du controleur pour l'URL : "
                            + url, e);
        }
    }

    private Method chercherMethodeParNom(
            Class<?> clazz, String methodName) {

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }

        return null;
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }
}

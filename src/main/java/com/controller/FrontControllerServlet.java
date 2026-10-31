package com.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import com.annotation.Authentification;
import com.annotation.JsonAnnotation;
import com.binding.BindingResult;
import com.binding.ParameterBinder;
import com.ioc.ApplicationContext;
import com.model.Mapping;
import com.model.ModelView;
import com.model.UrlMethod;
import com.utils.JsonConvertir;
import com.model.MySession;

@WebServlet(name = "FrontController", urlPatterns = {"/"})
public class FrontControllerServlet extends HttpServlet {

    private String viewPrefix;
    private String viewSuffix;
    private String sessionAuthAttribute;
    private ApplicationContext appContext;

    @Override
    public void init() throws ServletException {
        ServletContext ctx = getServletContext();

        this.appContext = (ApplicationContext) ctx.getAttribute("appContext");
        if (this.appContext == null) {
            throw new ServletException("Le conteneur IoC n'a pas ete initialise au demarrage.");
        }

        this.viewPrefix = (String) ctx.getAttribute("view-prefix");
        this.viewSuffix = (String) ctx.getAttribute("view-suffix");
        if (viewPrefix == null) viewPrefix = "/WEB-INF/Views/";
        if (viewSuffix == null) viewSuffix = ".jsp";

        // Le nom de l'attribut d'authentification vient de web.xml.
        this.sessionAuthAttribute = getServletConfig().getInitParameter("sessionAuthAttribute");
        if (sessionAuthAttribute == null || sessionAuthAttribute.trim().isEmpty()) {
            throw new ServletException(
                    "Le parametre init-param 'sessionAuthAttribute' doit etre configure dans web.xml.");
        }
        this.sessionAuthAttribute = sessionAuthAttribute.trim();

        // Disponible aussi pour les autres composants de l'application si necessaire.
        ctx.setAttribute("sessionAuthAttribute", this.sessionAuthAttribute);

        getServletContext().log("[AUTH] Attribut de session configure : " + this.sessionAuthAttribute);
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        @SuppressWarnings("unchecked")
        Map<UrlMethod, Mapping> mappingUrls =
                (Map<UrlMethod, Mapping>) getServletContext().getAttribute("mesRoutes");

        if (mappingUrls == null) {
            throw new ServletException("Le registre des routes n'a pas ete initialise au demarrage.");
        }

        String urlMain = request.getRequestURL().toString();
        String contextPath = request.getContextPath();
        String url = request.getRequestURI().substring(contextPath.length());
        String reqMethod = request.getMethod();
        UrlMethod urlMethod = new UrlMethod(url, reqMethod);

        if (!mappingUrls.containsKey(urlMethod)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Aucune route trouvee pour l'URL : " + url + " [" + reqMethod + "]");
            return;
        }

        try {
            Mapping mapping = mappingUrls.get(urlMethod);
            Class<?> controllerClass = Class.forName(mapping.getClassName());

            Object controller = obtenirControleur(controllerClass);
            if (controller == null) {
                throw new ServletException("Controller non trouve dans le conteneur : " + controllerClass.getName());
            }

            String methodName = mapping.getMethod();
            Method method = chercherMethodeParNom(controllerClass, methodName);
            if (method == null) {
                throw new ServletException("Methode non trouvee : " + methodName + " dans " + controllerClass.getName());
            }
            if (!method.canAccess(controller)) {
                throw new ServletException("Acces a la methode refuse : " + controllerClass.getName() + "." + methodName);
            }

            if (method.isAnnotationPresent(Authentification.class)
                    && !estAuthentifie(request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Authentification requise pour acceder a : " + url);
                return;
            }

            Object result;
            Object[] args = new Object[0];

            try {
                if (method.getParameterCount() > 0) {
                    BindingResult binding = ParameterBinder.bind(method, request, response);
                    if (binding != null) {
                        args = binding.getArguments();
                        if (binding.getValues() != null) {
                            for (Map.Entry<String, Object> e : binding.getValues().entrySet()) {
                                request.setAttribute(e.getKey(), e.getValue());
                            }
                        }
                    }
                    result = method.invoke(controller, args);
                } else {
                    result = method.invoke(controller);
                }
            } finally {
                synchroniserSessions(args, request);
            }

            if (method.isAnnotationPresent(JsonAnnotation.class)
                    || controllerClass.isAnnotationPresent(JsonAnnotation.class)) {
                response.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print(JsonConvertir.toJson(result));
                }
                return;
            }

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
            }

            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<h2>FrontController servlet</h2>");
                out.println("<p><strong>Current URL:</strong> " + urlMain + "</p>");
                out.println("<p>La methode a retourne : " + result + "</p>");
            }

        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'execution du controleur pour l'URL: " + url, e);
        }
    }

    /**
     * Une session existante doit contenir une valeur non nulle sous le nom
     * configure dans web.xml. getSession(false) evite de creer une session
     * pour une personne qui n'est pas encore connectee.
     */
    private boolean estAuthentifie(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object utilisateur = session.getAttribute(sessionAuthAttribute);
        return utilisateur != null;
    }

    /** Conserve une instance de controleur dans le ServletContext. */
    private Object obtenirControleur(Class<?> controllerClass) {
        ServletContext contexte = getServletContext();
        String cle = "controller:" + controllerClass.getName();
        Object controller = contexte.getAttribute(cle);
        if (controller != null) {
            return controller;
        }
        synchronized (contexte) {
            controller = contexte.getAttribute(cle);
            if (controller == null) {
                controller = appContext.getBean(controllerClass);
                if (controller != null) {
                    contexte.setAttribute(cle, controller);
                }
            }
        }
        return controller;
    }

    private void synchroniserSessions(Object[] args, HttpServletRequest request) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg instanceof MySession) {
                ((MySession) arg).synchroniserVers(request.getSession());
            }
        }
    }

    private Method chercherMethodeParNom(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) return m;
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

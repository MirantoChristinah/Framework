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
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import com.annotation.Authentification;
import com.annotation.JsonAnnotation;
import com.annotation.RoleRequired;
import com.binding.BindingResult;
import com.binding.ParameterBinder;
import com.ioc.ApplicationContext;
import com.model.Mapping;
import com.model.ModelView;
import com.model.MySession;
import com.model.UrlMethod;
import com.utils.JsonConvertir;

@WebServlet(name = "FrontController", urlPatterns = {"/"})
public class FrontControllerServlet extends HttpServlet {

    private static final String SESSION_ROLES_ATTRIBUTE = "roles";
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

        this.sessionAuthAttribute = getServletConfig().getInitParameter("sessionAuthAttribute");
        if (sessionAuthAttribute == null || sessionAuthAttribute.trim().isEmpty()) {
            throw new ServletException("Le parametre init-param 'sessionAuthAttribute' doit etre configure dans web.xml.");
        }
        this.sessionAuthAttribute = sessionAuthAttribute.trim();
        ctx.setAttribute("sessionAuthAttribute", this.sessionAuthAttribute);
        getServletContext().log("[AUTH] Attribut de session configure : " + this.sessionAuthAttribute);
        getServletContext().log("[AUTH] Attribut des roles configure : " + SESSION_ROLES_ATTRIBUTE);
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

            // @RoleRequired implique egalement une authentification.
            boolean authRequise = method.isAnnotationPresent(Authentification.class)
                    || method.isAnnotationPresent(RoleRequired.class);
            if (authRequise && !estAuthentifie(request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Authentification requise pour acceder a : " + url);
                return;
            }

            RoleRequired roleRequired = method.getAnnotation(RoleRequired.class);
            if (roleRequired != null && !possedeAuMoinsUnRole(request, roleRequired.value())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Role insuffisant pour acceder a : " + url);
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

    private boolean estAuthentifie(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(sessionAuthAttribute) != null;
    }

    /** Accepte une Collection, un tableau ou un role unique en String. */
    private boolean possedeAuMoinsUnRole(HttpServletRequest request, String[] autorises) {
        HttpSession session = request.getSession(false);
        if (session == null || autorises == null || autorises.length == 0) return false;
        Object roles = session.getAttribute(SESSION_ROLES_ATTRIBUTE);
        if (roles == null) return false;

        if (roles instanceof Collection<?>) {
            for (Object role : (Collection<?>) roles) {
                if (roleCorrespond(role, autorises)) return true;
            }
            return false;
        }
        if (roles.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(roles); i++) {
                if (roleCorrespond(Array.get(roles, i), autorises)) return true;
            }
            return false;
        }
        return roleCorrespond(roles, autorises);
    }

    private boolean roleCorrespond(Object roleSession, String[] autorises) {
        if (roleSession == null) return false;
        String role = String.valueOf(roleSession).trim();
        for (String autorise : autorises) {
            if (autorise != null && role.equalsIgnoreCase(autorise.trim())) return true;
        }
        return false;
    }

    private Object obtenirControleur(Class<?> controllerClass) {
        ServletContext contexte = getServletContext();
        String cle = "controller:" + controllerClass.getName();
        Object controller = contexte.getAttribute(cle);
        if (controller != null) return controller;
        synchronized (contexte) {
            controller = contexte.getAttribute(cle);
            if (controller == null) {
                controller = appContext.getBean(controllerClass);
                if (controller != null) contexte.setAttribute(cle, controller);
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

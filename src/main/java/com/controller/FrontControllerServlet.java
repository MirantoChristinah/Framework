package com.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import com.annotation.*;
import com.exception.*;
import com.model.*;

public class FrontControllerServlet extends HttpServlet {

        private Map<UrlMethod, UrlMapping> routes = new HashMap<>();

        @Override
        public void init() throws ServletException {
                routes = (Map<UrlMethod, UrlMapping>) getServletContext().getAttribute("routes");
        }

        private void processRequest(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {

                              
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                String urlMain = request.getRequestURL().toString();
                String contextPath = request.getContextPath();
                String url = request.getRequestURI().substring(contextPath.length());
                                
                String viewPrefix = (String) getServletContext().getAttribute("view-prefix");
                String viewSuffix = (String) getServletContext().getAttribute("view-suffix");

                out.println("<h2>FrontController servlet</h2>");
                out.println("<p><strong>Current URL:</strong> " + urlMain + "</p>");

                String reqMethod = request.getMethod();
                UrlMethod urlMethod = new UrlMethod(url, reqMethod);

                if (routes.containsKey(urlMethod)) {

                        try {
                                UrlMapping mapping = routes.get(urlMethod);
                                Object controller = mapping.getController()
                                .getDeclaredConstructor()
                                .newInstance();

                                Object result = mapping.getMethod()
                                                .invoke(controller);

                                if(result instanceof ModelView){

                                ModelView mv = (ModelView) result;

                                for(Map.Entry<String,Object> e : mv.getData().entrySet()){
                                        request.setAttribute(e.getKey(), e.getValue());
                                }

                                String view = viewPrefix + mv.getUrl() + viewSuffix;

                                request.getRequestDispatcher(view)
                                        .forward(request,response);

                                        return;
                                }
                                
                        } catch (Exception e) {
                                throw new ServletException(e);
                        }

                  
                } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                "No route found for " + url);
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
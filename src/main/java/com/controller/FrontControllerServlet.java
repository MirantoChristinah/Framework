package com.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class FrontControllerServlet extends HttpServlet {

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            String requestURI = request.getRequestURI();
            String contextPath = request.getContextPath();
            String urlSaisie = requestURI.substring(contextPath.length());

            out.println("<h1>Framework Test</h1>");
            out.println("<p>URL complete : " + requestURI + "</p>");
            out.println("<p>URL saisie detectee : <strong>" + urlSaisie + "</strong></p>");

            out.println("<p>DEBUG requestURI = [" + requestURI + "]</p>");
            out.println("<p>DEBUG contextPath = [" + contextPath + "]</p>");
            out.println("<p>DEBUG urlSaisie = [" + urlSaisie + "]</p>");
            out.println("<p>DEBUG servletPath = [" + request.getServletPath() + "]</p>");
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
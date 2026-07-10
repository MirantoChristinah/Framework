package com.utils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.annotation.*;
import com.model.UrlMethod;

import java.net.URL;

public class Utils {

    public static List<Object> findWithAnnotation(Class<? extends Annotation> annotation, String packageName,
            String niveau) throws Exception {

        List<Object> objects = new ArrayList<>();

        if (niveau.equalsIgnoreCase("class")) {
            List<Class<?>> listClasses = scanPackage(packageName);
            for (Class<?> class1 : listClasses) {
                if (class1.isAnnotationPresent(annotation)) {
                    objects.add(class1);
                }
            }
        } else if (niveau.equalsIgnoreCase("attribut")) {

        } else if (niveau.equalsIgnoreCase("method")) {

        } else {
            throw new Exception("Choose class, attribut, or method");
        }

        return objects;
    }

    public static List<Class<?>> getControllers(String packageName) {
        List<Class<?>> listClasses = new ArrayList<>();

        try {
            List<Object> listObject = findWithAnnotation(Controller.class, packageName, "class");
            for (Object o : listObject) {
                if (o instanceof Class<?>) {
                    listClasses.add((Class<?>) o);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return listClasses;
    }

    public static void buildRoutingTable(String packageName, Map<UrlMethod, UrlMapping> routes) {

        List<Class<?>> controllers = getControllers(packageName);

        for (Class<?> controller : controllers) {

            for (Method method : controller.getMethods()) {

                if (!method.isAnnotationPresent(UrlMapping.class)) {
                    continue;
                }

                String url = method.getAnnotation(UrlMapping.class).url();
                String httpMethod = method.getAnnotation(UrlMapping.class).method().toUpperCase();

                UrlMethod urlMethod = new UrlMethod(url, httpMethod);

                UrlMapping mapping = new UrlMapping();
                mapping.setController(controller);
                mapping.setMethod(method);
                mapping.setUrl(url);

                if (routes.containsKey(urlMethod)) {
                    throw new RuntimeException(
                            "Duplicate URL and method mapping detected : " + url + " [" + httpMethod + "]");
                }

                routes.put(urlMethod, mapping);
            }
        }

    }

    public static List<Class<?>> scanPackage(String packageName) throws Exception {

        if (packageName == null) {
            throw new IllegalArgumentException("package name cannot be null. Check your configuration file");
        }

        List<Class<?>> classes = new ArrayList<>();

        String path = packageName.replace('.', '/');

        URL resource = Utils.class
                .getClassLoader()
                .getResource(path);

        // System.out.println(resource);

        if (resource == null) {
            return classes;
        }

        File directory = new File(resource.getFile());

        scanDirectory(directory, packageName, classes);

        return classes;
    }

    private static void scanDirectory(File directory, String packageName, List<Class<?>> classes)
            throws ClassNotFoundException {

        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                scanDirectory(
                        file,
                        packageName + "." + file.getName(),
                        classes);

            } else if (file.getName().endsWith(".class")) {

                String className = packageName + "."
                        + file.getName().substring(
                                0,
                                file.getName().length() - 6);

                classes.add(Class.forName(className));
            }
        }
    }

    public static List<String> classesToString(List<Class<?>> list) {
        List<String> names = new ArrayList<>();
        for (Class<?> c : list) {
            names.add(c.getSimpleName());
        }
        return names;
    }

}

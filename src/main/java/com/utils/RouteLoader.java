package com.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
import com.annotation.AnnotationController;
import com.annotation.UrlMapping;
import com.exception.DuplicateUrlException;
import com.model.Mapping;
import com.model.UrlMethod;

public class RouteLoader {

    public static void buildRoutingTable(String packageName, Map<UrlMethod, Mapping> mappingUrls) throws Exception {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) return;

        File directory = new File(resource.getFile());
        if (directory.exists()) {
            scanDirectory(directory, packageName, mappingUrls);
        }
    }

    private static void scanDirectory(File directory, String packageName, Map<UrlMethod, Mapping> mappingUrls) throws Exception {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), mappingUrls);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                Class<?> cls = Class.forName(className);

                if (cls.isAnnotationPresent(AnnotationController.class)) {
                    for (Method method : cls.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(UrlMapping.class)) {
                            UrlMapping urlMapping = method.getAnnotation(UrlMapping.class);
                            
                            String urlValue = urlMapping.value();
                            String httpMethod = urlMapping.method().toUpperCase();

                            UrlMethod urlMethodKey = new UrlMethod(urlValue, httpMethod);

                            if (mappingUrls.containsKey(urlMethodKey)) {
                                throw new DuplicateUrlException(urlValue, httpMethod);
                            }

                            Mapping mapping = new Mapping(cls.getName(), method.getName());
                            mappingUrls.put(urlMethodKey, mapping);
                        }
                    }
                }
            }
        }
    }
}
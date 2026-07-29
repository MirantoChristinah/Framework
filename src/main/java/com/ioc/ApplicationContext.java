package com.ioc;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.annotation.*;

public class ApplicationContext {
    private Map<Class<?>, Object> singletons = new HashMap<>();

    // Enregistre une classe en tant que singleton
    public void register(Class<?> clazz) throws Exception {
        if (!singletons.containsKey(clazz)) {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            singletons.put(clazz, instance);
            injectDependencies(instance); // injecte les @Autowired
        }
    }

    // Injection de dépendances par réflexion
    private void injectDependencies(Object instance) throws Exception {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                Class<?> depType = field.getType();
                Object dependency = getBean(depType);
                if (dependency == null) {
                    throw new RuntimeException("Dépendance non trouvée : " + depType.getName());
                }
                field.setAccessible(true);
                field.set(instance, dependency);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        return (T) singletons.get(clazz);
    }

    public Map<Class<?>, Object> getAllBeans() {
        return singletons;
    }
}
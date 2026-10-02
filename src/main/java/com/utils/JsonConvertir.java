package com.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

public class JsonConvertir {

    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        serialize(obj, sb);
        return sb.toString();
    }

    private static void serialize(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof String) {
            serializeString((String) obj, sb);
        } else if (obj instanceof Character) {
            serializeString(String.valueOf(obj), sb);
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
        } else if (obj instanceof Map) {
            serializeMap((Map<?, ?>) obj, sb);
        } else if (obj instanceof Collection) {
            serializeCollection((Collection<?>) obj, sb);
        } else if (obj.getClass().isArray()) {
            serializeArray(obj, sb);
        } else {
            serializeObject(obj, sb);
        }
    }

    private static void serializeString(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void serializeMap(Map<?, ?> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            serializeString(String.valueOf(entry.getKey()), sb);
            sb.append(':');
            serialize(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private static void serializeCollection(Collection<?> collection, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : collection) {
            if (!first) sb.append(',');
            first = false;
            serialize(item, sb);
        }
        sb.append(']');
    }

    private static void serializeArray(Object array, StringBuilder sb) {
        sb.append('[');
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(',');
            serialize(Array.get(array, i), sb);
        }
        sb.append(']');
    }

    private static void serializeObject(Object obj, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (!first) sb.append(',');
                first = false;
                serializeString(field.getName(), sb);
                sb.append(':');
                serialize(value, sb);
            } catch (IllegalAccessException e) {
                // champ inaccessible : on l'ignore
            }
        }
        sb.append('}');
    }
}
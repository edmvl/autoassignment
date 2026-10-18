package ru.gpb.jira.plugin.autoassignment.util;

import org.codehaus.jackson.map.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Small JSON helper backed by the Jackson 1.x library provided by JIRA.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> List<T> toList(String json, Class<T> elementType) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}

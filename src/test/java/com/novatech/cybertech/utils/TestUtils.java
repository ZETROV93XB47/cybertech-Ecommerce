package com.novatech.cybertech.utils;

import tools.jackson.databind.ObjectMapper;


public class TestUtils {
    public static String asJsonString(final Object obj) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

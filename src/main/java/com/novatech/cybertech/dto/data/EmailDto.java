package com.novatech.cybertech.dto.data;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class EmailDto {
    private String to;
    private String from;
    private String subject;
    private String templatePath;
    private Map<String, Object> templateVariables;
}

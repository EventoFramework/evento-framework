package com.evento.server.web.dto;

import com.evento.common.modeling.bundle.types.ComponentType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;

@Data
public class ConsumerDTO {

    private String bundleId;
    private long bundleVersion;
    private String componentName;
    private String componentVersion;
    private ComponentType componentType;
    private String context;
    private String consumerId;
    private HashSet<String> instances;

}

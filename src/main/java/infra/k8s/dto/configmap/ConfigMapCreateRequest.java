package infra.k8s.dto.configmap;

import lombok.Data;

import java.util.Map;

@Data
public class ConfigMapCreateRequest {
    private String name;
    private String namespace;
    private Map<String, String> data;
}
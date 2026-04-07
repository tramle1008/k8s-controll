package infra.k8s.dto.common;

import lombok.Data;

import java.util.Map;

@Data
public class MetadataDto {
    private String name;
    private String namespace;
    private Map<String, String> labels;
    private Map<String, String> annotations;
}
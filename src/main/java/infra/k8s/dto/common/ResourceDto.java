package infra.k8s.dto.common;

import lombok.Data;

import java.util.Map;

@Data
public class ResourceDto {

    private Map<String,String> limits;

    private Map<String,String> requests;
}
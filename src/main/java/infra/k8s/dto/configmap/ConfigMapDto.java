package infra.k8s.dto.configmap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigMapDto {
    private String name;
    private String namespace;
    private int dataCount;
    private String age;

}
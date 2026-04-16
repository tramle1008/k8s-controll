package infra.k8s.dto.ingress;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class IngressDTO {
    private String name;
    private String namespace;
    private List<String> hosts;
    private String loadBalancerIp;
}
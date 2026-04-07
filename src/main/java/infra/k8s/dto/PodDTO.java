package infra.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodDTO {

    private String name;
    private String namespace;
    private String status;
    private String podIp;
    private String nodeName;
}
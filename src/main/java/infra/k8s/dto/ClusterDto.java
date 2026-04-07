package infra.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClusterDto {
    private Long id;
    private String name;
}
package infra.k8s.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
@AllArgsConstructor
public class AddonStatusDTO {
    private String name;
    private String namespace;
    private boolean installed;
    private boolean healthy;
}
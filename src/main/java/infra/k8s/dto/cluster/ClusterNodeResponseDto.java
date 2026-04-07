package infra.k8s.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import infra.k8s.Context.NodeRole;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterNodeResponseDto {
    private Long id;
    private String name;
    private NodeRole role;
    private String ipAddress;
    private String username;
}
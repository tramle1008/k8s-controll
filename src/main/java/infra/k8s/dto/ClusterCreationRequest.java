package infra.k8s.dto;
import lombok.Data;
import java.util.List;

//tao cluster
@Data
public class ClusterCreationRequest {
    private String clusterName;
    private List<NodeDto> nodes;
}
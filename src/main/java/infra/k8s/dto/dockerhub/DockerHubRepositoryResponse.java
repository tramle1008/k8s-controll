package infra.k8s.dto.dockerhub;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DockerHubRepositoryResponse {
    private int count;
    private String next;
    private String previous;
    private List<DockerHubRepositoryItem> results;
}

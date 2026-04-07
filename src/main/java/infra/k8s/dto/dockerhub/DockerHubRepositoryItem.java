package infra.k8s.dto.dockerhub;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class DockerHubRepositoryItem {
    private String name;
    private String namespace;
    private String description;

}

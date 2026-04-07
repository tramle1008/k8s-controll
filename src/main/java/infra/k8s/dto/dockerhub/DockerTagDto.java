package infra.k8s.dto.dockerhub;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DockerTagDto {
    private String name;
    private String lastUpdated;
}

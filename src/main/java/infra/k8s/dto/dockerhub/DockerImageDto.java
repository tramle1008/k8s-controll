package infra.k8s.dto.dockerhub;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class DockerImageDto {
    private String imageName;
    private String fullName;
    private String description;


}

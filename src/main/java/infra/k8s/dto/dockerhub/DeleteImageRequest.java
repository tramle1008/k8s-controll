package infra.k8s.dto.dockerhub;

import lombok.Data;

@Data
public class DeleteImageRequest {
    private String repo; // user1/react-frontend
    private String tag;  // v1.0.0
}
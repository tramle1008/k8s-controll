package infra.k8s.dto.dockerhub;

import lombok.Data;

@Data
public class UploadImageRequest {
    private String username;
    private String appName;
    private String tag; // optional
}
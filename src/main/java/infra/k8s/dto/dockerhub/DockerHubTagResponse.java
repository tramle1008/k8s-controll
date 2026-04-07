package infra.k8s.dto.dockerhub;

import lombok.Data;

import java.util.List;

@Data
public class DockerHubTagResponse {
    private int count;
    private String next;
    private String previous;
    private List<DockerHubTagItem> results;
}

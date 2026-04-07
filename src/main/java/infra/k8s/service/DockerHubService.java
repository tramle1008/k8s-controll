package infra.k8s.service;

import infra.k8s.dto.dockerhub.DockerImageDto;
import infra.k8s.dto.dockerhub.DockerTagDto;

import java.util.List;

public interface DockerHubService {
    List<DockerImageDto> getImagesFromProfileLink(String link);

    List<DockerTagDto> getTagsFromProfileLink(String link, String imageName);
}

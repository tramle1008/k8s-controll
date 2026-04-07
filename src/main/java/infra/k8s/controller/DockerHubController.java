package infra.k8s.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import infra.k8s.dto.dockerhub.DockerImageDto;
import infra.k8s.dto.dockerhub.DockerTagDto;
import infra.k8s.service.DockerHubService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dockerhub")
public class DockerHubController {

    private final DockerHubService dockerHubService;

    @GetMapping("/images")
    public List<DockerImageDto> getImages(@RequestParam String link) {
        return dockerHubService.getImagesFromProfileLink(link);
    }

    @GetMapping("/tags")
    public List<DockerTagDto> getTags(
            @RequestParam String link,
            @RequestParam String imageName
    ) {
        return dockerHubService.getTagsFromProfileLink(link, imageName);
    }
}
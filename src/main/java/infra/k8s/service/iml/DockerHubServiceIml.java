package infra.k8s.service.iml;

import infra.k8s.dto.dockerhub.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rancher.k8s.dto.dockerhub.*;
import infra.k8s.service.DockerHubService;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DockerHubServiceIml implements DockerHubService {
    private final RestTemplate restTemplate;

    public List<DockerImageDto> getImagesFromProfileLink(String profileLink) {
        String namespace = extractNamespaceFromDockerHubLink(profileLink);
        return getImagesByNamespace(namespace);
    }

    public List<DockerTagDto> getTagsFromProfileLink(String profileLink, String imageName) {
        String namespace = extractNamespaceFromDockerHubLink(profileLink);
        return getTags(namespace, imageName);
    }

    public List<DockerImageDto> getImagesByNamespace(String namespace) {
        List<DockerImageDto> images = new ArrayList<>();

        String url = "https://hub.docker.com/v2/namespaces/" + namespace + "/repositories?page_size=100";

        while (url != null && !url.isBlank()) {
            ResponseEntity<DockerHubRepositoryResponse> response =
                    restTemplate.exchange(URI.create(url), HttpMethod.GET, null, DockerHubRepositoryResponse.class);

            DockerHubRepositoryResponse body = response.getBody();
            if (body == null || body.getResults() == null) {
                break;
            }

            for (DockerHubRepositoryItem item : body.getResults()) {
                images.add(new DockerImageDto(
                        item.getName(),
                        item.getNamespace() + "/" + item.getName(),
                        item.getDescription()
                ));
            }

            url = body.getNext();
        }

        return images;
    }

    public List<DockerTagDto> getTags(String namespace, String imageName) {
        List<DockerTagDto> tags = new ArrayList<>();

        String url = "https://hub.docker.com/v2/namespaces/" + namespace +
                "/repositories/" + imageName + "/tags?page_size=100";

        while (url != null && !url.isBlank()) {
            ResponseEntity<DockerHubTagResponse> response =
                    restTemplate.exchange(URI.create(url), HttpMethod.GET, null, DockerHubTagResponse.class);

            DockerHubTagResponse body = response.getBody();
            if (body == null || body.getResults() == null) {
                break;
            }

            for (DockerHubTagItem item : body.getResults()) {
                tags.add(new DockerTagDto(
                        item.getName(),
                        item.getLast_updated()
                ));
            }

            url = body.getNext();
        }

        return tags;
    }

    public String extractNamespaceFromDockerHubLink(String link) {
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("Link Docker Hub không được rỗng");
        }

        try {
            URI uri = new URI(link.trim());
            String path = uri.getPath();

            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Link Docker Hub không hợp lệ");
            }

            String[] parts = path.split("/");
            if (parts.length >= 3 && "u".equals(parts[1]) && !parts[2].isBlank()) {
                return parts[2];
            }

            throw new IllegalArgumentException("Chỉ hỗ trợ link dạng https://hub.docker.com/u/{username}");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Link Docker Hub không hợp lệ");
        }
    }
}

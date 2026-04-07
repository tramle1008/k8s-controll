package infra.k8s.service.iml;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.Context.RegistryProperties;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.RegistryService;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RegistryServiceIml implements RegistryService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ClusterManager clusterManager;
    private final RegistryProperties registryProperties;

    @Override
    public String handleUpload(
            MultipartFile file,
            String username,
            String appName,
            String customTag
    ) throws Exception {

        String safeUsername = sanitize(username);
        String safeAppName = sanitize(appName);

        String version = "v" + System.currentTimeMillis();

        File tempFile = File.createTempFile("upload-", ".tar");

        try {
            file.transferTo(tempFile);

            String loadOutput = runCommand("docker load -i \"" + tempFile.getAbsolutePath() + "\"");

            String loadedImage = parseLoadedImage(loadOutput);

            String tag = (customTag != null && !customTag.isBlank())
                    ? sanitize(customTag)
                    : "v" + System.currentTimeMillis();

            String newImage = registryProperties.getHost()
                    + "/" + safeUsername + "/" + safeAppName + ":" + tag;

            runCommand("docker tag " + loadedImage + " " + newImage);
            runCommand("docker push " + newImage);

            return newImage;

        } finally {
            tempFile.delete();
        }
    }

    @Override
    public List<String> listImagesByUser(String username) {
        String url = "http://192.168.235.150:5000/v2/_catalog";
        RestTemplate rest = new RestTemplate();
        Map<String, Object> catalog = rest.getForObject(url, Map.class);

        List<String> repos = (List<String>) catalog.get("repositories");
        return repos.stream()
                .filter(r -> r.startsWith(username + "/"))
                .collect(Collectors.toList());
    }

    private String sanitize(String input) {
        if (input == null) return null;

        return input
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")  // thay ký tự lạ thành -
                .replaceAll("-+", "-")          // tránh ----
                .replaceAll("^-|-$", "");       // bỏ - đầu/cuối
    }

    private String runCommand(String command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder();

        // Windows dùng cmd
        builder.command("cmd.exe", "/c", command);
        builder.redirectErrorStream(true);

        Process process = builder.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + output);
        }

        return output.toString();
    }

    private String parseLoadedImage(String output) {
        // tìm dòng: Loaded image: xxx
        for (String line : output.split("\n")) {
            if (line.contains("Loaded image:")) {
                return line.split("Loaded image:")[1].trim();
            }
        }
        throw new RuntimeException("Không tìm thấy image sau khi load");
    }




    @Override
    public List<String> getTags(String repo) {
        String url = registryProperties.getUrl() + "/v2/" + repo + "/tags/list";

        Map response = restTemplate.getForObject(url, Map.class);

        return (List<String>) response.get("tags");
    }

    @Override
    public void deleteImage(String repo, String tag) throws Exception {

        String fullImage = registryProperties.getHost() + "/" + repo + ":" + tag;

        KubernetesClient client = clusterManager.requireActiveClient();

        // ================= 1. CHECK DEPLOYMENT =================
        boolean inUse = isImageInUse(client, repo, tag);

        if (inUse) {
            throw new RuntimeException("Image đang được sử dụng bởi deployment");
        }

        // ================= 2. LẤY DIGEST =================
        String digest = getDigest(repo, tag);

        // ================= 3. DELETE =================
        deleteByDigest(repo, digest);
    }

    // ================= DELETE =================
    private void deleteByDigest(String repo, String digest) {

        String url = registryProperties.getUrl()
                + "/v2/" + repo + "/manifests/" + digest;

        restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                null,
                Void.class
        );
    }
    private boolean isImageInUse(KubernetesClient client, String repo, String tag) {

        String target = repo + ":" + tag;

        // check Deployment
        boolean inDeployment = client.apps().deployments().inAnyNamespace().list().getItems()
                .stream()
                .flatMap(d -> d.getSpec().getTemplate().getSpec().getContainers().stream())
                .anyMatch(c -> c.getImage() != null && c.getImage().contains(target));

        if (inDeployment) return true;

        // check Pod (quan trọng)
        boolean inPod = client.pods().inAnyNamespace().list().getItems()
                .stream()
                .flatMap(p -> p.getSpec().getContainers().stream())
                .anyMatch(c -> c.getImage() != null && c.getImage().contains(target));

        return inPod;
    }
    private String getDigest(String repo, String tag) {

        String url = registryProperties.getUrl()
                + "/v2/" + repo + "/manifests/" + tag;

        HttpHeaders headers = new HttpHeaders();

        headers.set("Accept",
                "application/vnd.oci.image.index.v1+json," +
                        "application/vnd.oci.image.manifest.v1+json," +
                        "application/vnd.docker.distribution.manifest.v2+json"
        );

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                String.class
        );

        System.out.println("Headers: " + response.getHeaders());

        List<String> digestHeaders = response.getHeaders().get("Docker-Content-Digest");

        if (digestHeaders == null || digestHeaders.isEmpty()) {
            throw new RuntimeException("Không lấy được digest từ registry");
        }

        return digestHeaders.get(0);
    }
}

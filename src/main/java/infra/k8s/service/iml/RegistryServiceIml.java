package infra.k8s.service.iml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import infra.k8s.dto.Registry.ImageTagDTO;
import infra.k8s.dto.Registry.RepositoryDTO;
import infra.k8s.util.SSHService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.Context.RegistryProperties;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.RegistryService;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RegistryServiceIml implements RegistryService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ClusterManager clusterManager;
    private final RegistryProperties registryProperties;

    private final String REGISTRY_API = "http://192.168.235.150:5000";
    private final String REGISTRY_CONTAINER = "registry";
    private final SSHService sshService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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


    @Override
    public void deleteRepository(String repo) throws Exception {

        // ======================================================
        // 1. Lấy danh sách tags của repo
        // ======================================================
        String listCmd = "curl -s http://192.168.235.150:5000/v2/" + repo + "/tags/list";
        String listOutput = sshService.exec(listCmd);

        List<String> tags = new ArrayList<>();

        // Trường hợp repo không tồn tại
        if (listOutput.contains("NOT_FOUND")) {
            throw new RuntimeException("Repo không tồn tại: " + repo);
        }

        // Nếu có tags
        if (listOutput.contains("\"tags\"")) {

            String tagSection = listOutput.split("\"tags\"")[1];

            // Nếu tags = null
            if (tagSection.contains("null")) {
                System.out.println("ℹ Repo " + repo + " không có tag nào → skip bước xoá manifest");
            } else {
                // Parse danh sách tags
                String tagBlock = tagSection.substring(tagSection.indexOf("[") + 1, tagSection.indexOf("]"));
                for (String t : tagBlock.split(",")) {
                    String clean = t.replace("\"", "").trim();
                    if (!clean.isEmpty()) tags.add(clean);
                }
            }
        }

        // ======================================================
        // 2. Xoá tag nếu có
        // ======================================================
        if (!tags.isEmpty()) {

            for (String tag : tags) {

                String curlCmd =
                        "curl -I -H \"Accept: application/vnd.docker.distribution.manifest.v2+json\" " +
                                "http://192.168.235.150:5000/v2/" + repo + "/manifests/" + tag;

                String headerOutput = sshService.exec(curlCmd);

                String digest = null;
                for (String line : headerOutput.split("\n")) {
                    if (line.startsWith("Docker-Content-Digest:")) {
                        digest = line.replace("Docker-Content-Digest:", "").trim();
                    }
                }

                if (digest == null) {
                    System.out.println("⚠ Không tìm thấy digest của tag: " + tag);
                    continue;
                }

                // Xoá manifest
                String deleteCmd =
                        "curl -X DELETE http://192.168.235.150:5000/v2/"
                                + repo + "/manifests/" + digest;

                sshService.exec(deleteCmd);
                System.out.println("🗑 Đã xoá tag " + tag + " (digest=" + digest + ")");
            }

        } else {
            System.out.println("ℹ Repo không có tag nào → bỏ qua xoá manifest.");
        }


        // ======================================================
        // 3. XÓA THƯ MỤC REPO RỖNG
        // ======================================================
        String rmCmd =
                "docker exec registry rm -rf /var/lib/registry/docker/registry/v2/repositories/" + repo;

        sshService.exec(rmCmd);


        // ======================================================
        // 4. Garbage Collect
        // ======================================================
        String gcCmd =
                "docker exec registry registry garbage-collect /etc/docker/registry/config.yml";

        sshService.exec(gcCmd);

        System.out.println("✅ Đã xoá toàn bộ repo: " + repo);
    }

    @Override
    public List<RepositoryDTO> listAllRepositories() throws Exception {
        List<RepositoryDTO> reposList = new ArrayList<>();

        // Bước 1: Lấy tất cả repo
        String catalogJson = sshService.exec("curl -s http://192.168.235.150:5000/v2/_catalog");
        JsonNode catalogNode = objectMapper.readTree(catalogJson);
        Iterator<JsonNode> repoIter = catalogNode.get("repositories").elements();

        while (repoIter.hasNext()) {
            String repoName = repoIter.next().asText();
            List<ImageTagDTO> tagsList = new ArrayList<>();

            // Bước 2: Lấy tag của repo
            String tagsJson = sshService.exec("curl -s http://192.168.235.150:5000/v2/" + repoName + "/tags/list");
            JsonNode tagsNode = objectMapper.readTree(tagsJson).get("tags");
            if (tagsNode != null) {
                for (JsonNode tagNode : tagsNode) {
                    String tag = tagNode.asText();

                    // Bước 3: Lấy manifest của tag để tính size
                    String manifestJson = sshService.exec(
                            "curl -s -H \"Accept: application/vnd.docker.distribution.manifest.v2+json\" " +
                                    "http://192.168.235.150:5000/v2/" + repoName + "/manifests/" + tag
                    );
                    JsonNode manifestNode = objectMapper.readTree(manifestJson);

                    long size = 0;
                    JsonNode configNode = manifestNode.get("config");
                    if (configNode != null && configNode.has("size")) {
                        size += configNode.get("size").asLong();
                    }
                    JsonNode layersNode = manifestNode.get("layers");
                    if (layersNode != null) {
                        for (JsonNode layer : layersNode) {
                            size += layer.get("size").asLong();
                        }
                    }

                    // Bước 4: Lấy thời gian push tag từ file 'link'
                    String linkPath = "/var/lib/registry/docker/registry/v2/repositories/"
                            + repoName + "/_manifests/tags/" + tag + "/current/link";

                    // Dùng stat để lấy thời gian thay đổi file
                    String pushTime = sshService.exec("stat -c %y " + linkPath).trim();

                    tagsList.add(new ImageTagDTO(tag, size, pushTime));
                }
            }

            reposList.add(new RepositoryDTO(repoName, tagsList));
        }

        return reposList;
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



    // Hàm tiện ích chạy docker exec từ Java
    private void runDockerExec(String container, String... cmd) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("exec");
        args.add(container);
        args.addAll(Arrays.asList(cmd));

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            System.out.println("[registry] " + line);
        }

        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Exec command failed: " + String.join(" ", cmd));
        }
    }
}
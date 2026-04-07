package infra.k8s.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import infra.k8s.module.Cluster;
import infra.k8s.module.ClusterNode;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
@RequiredArgsConstructor
public class SshKeyService {

    @Value("${ansible.control.private-key-path}")
    private String ansiblePrivateKeyPath;

    private static final int SSH_CONNECT_TIMEOUT = 10000;
    private static final int SSH_COMMAND_TIMEOUT = 20000;
    private static final int MAX_PARALLEL_SSH = 10;

    /**
     * Đọc public key
     */
    private String getAnsiblePublicKey() throws IOException {
        Path pubPath = Paths.get(ansiblePrivateKeyPath + ".pub");

        if (!Files.exists(pubPath)) {
            throw new IllegalStateException("Ansible public key file not found: " + pubPath);
        }

        return Files.readString(pubPath).trim();
    }

    /**
     * Deploy public key song song tới tất cả node
     */
    public DeployResult deployAnsibleKeyToNodes(Cluster cluster, Map<String, String> nodePasswords) throws Exception {

        String publicKey = getAnsiblePublicKey();

        List<String> success = Collections.synchronizedList(new ArrayList<>());
        List<String> failed = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_SSH);
        List<Future<?>> futures = new ArrayList<>();

        for (ClusterNode node : cluster.getClusterNodes()) {

            futures.add(executor.submit(() -> {
                try {
                    String password = nodePasswords.get(node.getName());

                    if (password == null || password.trim().isEmpty()) {
                        throw new RuntimeException("Thiếu password cho node");
                    }

                    deployPublicKeyToNode(node, publicKey, password.trim());

                    success.add(node.getName());
                    log.info("SSH OK: {}", node.getName());

                } catch (Exception e) {
                    failed.add(node.getName() + ": " + e.getMessage());
                    log.error("SSH FAIL {}: {}", node.getName(), e.getMessage());
                }
            }));

        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }

        executor.shutdown();

        return new DeployResult(success, failed);
    }

    private void deployPublicKeyToNode(ClusterNode node, String publicKey, String password) throws Exception {

        JSch jsch = new JSch();

        Session session = jsch.getSession(
                node.getUsername(),
                node.getIpAddress(),
                node.getSshPort() != null ? node.getSshPort() : 22
        );

        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(SSH_CONNECT_TIMEOUT);

        log.info("SSH connected: {}@{}:{}", node.getUsername(), node.getIpAddress(), node.getSshPort());

        ChannelExec channel = null;

        try {
            String command =
                    "mkdir -p ~/.ssh && " +
                            "chmod 700 ~/.ssh && " +
                            "echo '" + publicKey + "' >> ~/.ssh/authorized_keys && " +
                            "chmod 600 ~/.ssh/authorized_keys";

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            InputStream inputStream = channel.getInputStream();
            channel.connect();

            long start = System.currentTimeMillis();
            byte[] buffer = new byte[1024];

            while (true) {

                while (inputStream.available() > 0) {
                    int i = inputStream.read(buffer, 0, 1024);
                    if (i < 0) break;
                }

                if (channel.isClosed()) {
                    int exitStatus = channel.getExitStatus();

                    if (exitStatus != 0) {
                        throw new RuntimeException("Command exit code: " + exitStatus);
                    }

                    break;
                }

                if (System.currentTimeMillis() - start > SSH_COMMAND_TIMEOUT) {
                    throw new RuntimeException("SSH command timeout");
                }

                Thread.sleep(200);
            }

        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            session.disconnect();
        }
    }

    /**
     * Result class
     */
    public static class DeployResult {

        public final List<String> success;
        public final List<String> failed;

        public DeployResult(List<String> success, List<String> failed) {
            this.success = success;
            this.failed = failed;
        }

        public boolean allSuccess() {
            return failed.isEmpty();
        }
    }

}
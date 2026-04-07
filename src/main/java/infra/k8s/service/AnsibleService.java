package infra.k8s.service;

import com.jcraft.jsch.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import infra.k8s.Context.NodeRole;
import infra.k8s.dto.ClusterCreationRequest;
import infra.k8s.dto.NodeDto;
import infra.k8s.module.Cluster;
import infra.k8s.module.ClusterNode;
import infra.k8s.repository.ClusterNodeRepository;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.service.Informer.ClusterLogService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class AnsibleService {

    private final ClusterRepository clusterRepository;
    private final ClusterNodeRepository clusterNodeRepository; // nếu bạn có repo riêng cho node
    private final CryptoService cryptoService;
    @Value("${ansible.control.host:192.168.235.150}")
    private String ansibleHost;

    @Value("${ansible.control.user:luanvan}")
    private String ansibleUser;

    @Value("${ansible.control.private-key-path:/home/luanvan/.ssh/id_ed25519}")
    private String privateKeyPath;

    @Value("${ansible.playbook-dir:/home/luanvan/k8s-ansible-2}")
    private String playbookDir;
    private final ClusterLogService clusterLogService;

    public String uploadInventory(Cluster cluster) {
        log.warn("Bắt đầu tạo và upload inventory");

        String yamlContent = generateInventoryYaml(cluster);

        String user = "luanvan";
        String host = "192.168.235.150";
        String privateKey = "C:/Users/HP/.ssh/id_ed25519";

        String remoteFileName =
                "/home/luanvan/k8s-ansible-2/inventory/inventory-"
                        + cluster.getName() + ".yaml";

        Session session = null;
        ChannelSftp sftp = null;

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(privateKey);

            session = jsch.getSession(user, host, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();

            // đảm bảo thư mục tồn tại
            try {
                sftp.cd("/home/luanvan/k8s-ansible-2");
            } catch (SftpException e) {
                sftp.mkdir("/home/luanvan/k8s-ansible-2");
            }

            // ghi trực tiếp từ memory
            InputStream inputStream =
                    new ByteArrayInputStream(
                            yamlContent.getBytes(StandardCharsets.UTF_8)
                    );

            sftp.put(inputStream, remoteFileName);

            log.info("Upload inventory thành công: {}", remoteFileName);
            return remoteFileName;

        } catch (Exception e) {
            throw new RuntimeException("Upload inventory thất bại", e);
        } finally {
            if (sftp != null) sftp.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private String generateInventoryYaml(Cluster cluster) {
        log.warn("bat dau tao file yaml");
        StringBuilder yaml = new StringBuilder();
        yaml.append("all:\n");
        yaml.append("  hosts:\n");

        for (ClusterNode node : cluster.getClusterNodes()) {
            yaml.append("    ").append(node.getName()).append(":\n");
            yaml.append("      ansible_host: ").append(node.getIpAddress()).append("\n");
            yaml.append("      ansible_user: ").append(node.getUsername()).append("\n");
        }

        yaml.append("  children:\n");
        yaml.append("    kube_control_plane:\n");
        yaml.append("      hosts:\n");
        cluster.getClusterNodes().stream()
                .filter(n -> "master".equalsIgnoreCase(n.getRole().name()))
                .forEach(n -> yaml.append("        ").append(n.getName()).append(":\n"));

        yaml.append("    kube_node:\n");
        yaml.append("      hosts:\n");
        cluster.getClusterNodes().stream()
                .filter(n -> "worker".equalsIgnoreCase(n.getRole().name()))
                .forEach(n -> yaml.append("        ").append(n.getName()).append(":\n"));

        yaml.append("  vars:\n");
        yaml.append("    ansible_become: yes\n");
        yaml.append("    ansible_become_method: sudo\n");
        yaml.append("    ansible_ssh_common_args: '-o StrictHostKeyChecking=no'\n");
        yaml.append("    cluster_name: ").append(cluster.getName()).append("\n");
        yaml.append("    registry_address: 192.168.235.150:5000\n");

        return yaml.toString();
    }

    /**
     * Chạy ansible-playbook trên máy control (nat-ansible) từ backend Windows
     * @param remoteInventoryPath đường dẫn file inventory tren may ao
     * @param playbookName tên file playbook (common.yml, master-init.yml, ...)
     */
    public boolean runPlaybook(Long clusterId, String remoteInventoryPath, String playbookName) {

        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(privateKeyPath);

            session = jsch.getSession(ansibleUser, ansibleHost, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            String command = String.format(
                    "ansible-playbook -i %s %s/%s ",
                    remoteInventoryPath,
                    playbookDir,
                    playbookName
            );

            log.info("Chạy Ansible: {}", command);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            InputStream input = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String line;
            while ((line = reader.readLine()) != null) {
                String logLine = "[Ansible " + playbookName + "] " + line;

                log.info(logLine);

                // gửi log realtime qua websocket
                clusterLogService.sendLog(clusterId, logLine);
            }

            while (!channel.isClosed()) {
                Thread.sleep(500);
            }

            int exitStatus = channel.getExitStatus();
            log.info("Playbook '{}' hoàn tất với exit code: {}", playbookName, exitStatus);

            return exitStatus == 0;

        } catch (Exception e) {
            clusterLogService.sendLog(
                    clusterId,
                    "Lỗi chạy playbook '" + playbookName + "': " + e.getMessage()
            );

            log.error("Lỗi chạy playbook '{}': {}", playbookName, e.getMessage(), e);
            return false;
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    @Transactional
    public void fetchAndStoreAdminConf(Long clusterId) {

        Cluster cluster = clusterRepository.findClusterWithNodes(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster không tồn tại: " + clusterId));

        // Tìm master node
        ClusterNode masterNode = cluster.getClusterNodes().stream()
                .filter(n -> "MASTER".equalsIgnoreCase(n.getRole().name()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Không tìm thấy master node"));

        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(privateKeyPath);

            session = jsch.getSession(masterNode.getUsername(), masterNode.getIpAddress(), 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            String command = "sudo cat /etc/kubernetes/admin.conf";

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            InputStream inputStream = channel.getInputStream();
            channel.connect();

            StringBuilder contentBuilder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }

            while (!channel.isClosed()) {
                Thread.sleep(200);
            }

            int exitStatus = channel.getExitStatus();
            if (exitStatus != 0) {
                throw new RuntimeException("Đọc admin.conf thất bại, exit code: " + exitStatus);
            }

            String adminConfContent = contentBuilder.toString();
            log.info("Đọc admin.conf thành công, độ dài: {}", adminConfContent.length());

            // encrypt
            String encrypted = cryptoService.encrypt(adminConfContent);

            cluster.setEncryptedKubeconfig(encrypted);
            clusterRepository.saveAndFlush(cluster);
            Cluster check = clusterRepository.findById(clusterId).orElseThrow();

            log.info("DEBUG kubeconfig length in DB: {}",
                    check.getEncryptedKubeconfig() == null ? 0 :
                            check.getEncryptedKubeconfig().length());
            log.info("Đã mã hóa và lưu kubeconfig vào DB cho cluster {}", cluster.getName());

        } catch (Exception e) {
            log.error("Lỗi khi lấy admin.conf: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể lấy admin.conf", e);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    public void addWorker(Long clusterId, ClusterCreationRequest request) {

        log.info("Bắt đầu thêm worker vào cluster {}", clusterId);

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster không tồn tại"));

        List<String> newWorkers = new ArrayList<>();

        // 1️ Lưu worker mới vào DB
        for (NodeDto node : request.getNodes()) {

            ClusterNode worker = new ClusterNode();
            worker.setName(node.getName());
            worker.setIpAddress(node.getIp());
            worker.setUsername(node.getUser());
            worker.setCluster(cluster);
            worker.setRole(NodeRole.WORKER);

            clusterNodeRepository.save(worker);

            newWorkers.add(node.getName());

            log.info("Đã lưu worker {} vào DB", node.getName());
        }

        // 2️ reload cluster để lấy node mới
        cluster = clusterRepository.findById(clusterId).orElseThrow();

        // 3️ generate inventory
        String inventoryPath = uploadInventory(cluster);

        log.info("Inventory mới: {}", inventoryPath);

        // 4️. chạy ansible cho từng worker mới
        for (String workerName : newWorkers) {

            log.info("Chạy ansible add worker: {}", workerName);
            runPlaybookWithLimit(inventoryPath, "common.yml", workerName);
            runPlaybookWithLimit(inventoryPath, "containerd-registry-config.yml", workerName);
            runPlaybookWithLimit(inventoryPath, "workers-join.yml", workerName);
        }

        log.info("Thêm worker hoàn tất");
    }

    public boolean runPlaybookWithLimit(String inventoryPath, String playbookName, String nodeName) {

        Session session = null;
        ChannelExec channel = null;

        try {

            JSch jsch = new JSch();
            jsch.addIdentity(privateKeyPath);

            session = jsch.getSession(ansibleUser, ansibleHost, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            String command = String.format(
                    "ansible-playbook -i %s %s/%s --limit %s",
                    inventoryPath,
                    playbookDir,
                    playbookName,
                    nodeName
            );

            log.info("Chạy Ansible: {}", command);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            InputStream input = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String line;

            while ((line = reader.readLine()) != null) {
                log.info("[Ansible {}] {}", nodeName, line);
            }

            while (!channel.isClosed()) {
                Thread.sleep(500);
            }

            int exitStatus = channel.getExitStatus();

            log.info("Playbook '{}' cho node {} hoàn tất với exit code {}",
                    playbookName, nodeName, exitStatus);

            return exitStatus == 0;

        } catch (Exception e) {

            log.error("Lỗi chạy playbook cho node {}: {}", nodeName, e.getMessage(), e);
            return false;

        } finally {

            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }
    @Transactional
    public void removeWorker(Long clusterId, String nodeName) {

        log.info("Bắt đầu remove worker {} khỏi cluster {}", nodeName, clusterId);

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster không tồn tại"));

        ClusterNode worker = clusterNodeRepository
                .findByClusterIdAndName(clusterId, nodeName)
                .orElseThrow(() -> new RuntimeException("Worker không tồn tại"));
        if (worker.getEvents() != null) {
            Hibernate.initialize(worker.getEvents());
        }

        ClusterNode master = clusterNodeRepository
                .findFirstByClusterIdAndRole(clusterId, NodeRole.MASTER)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy master node"));

        boolean drained = false;
        boolean deleted = false;

        try {

            // 1️⃣ Drain node
            String drainCmd = String.format(
                    "kubectl drain %s --ignore-daemonsets --delete-emptydir-data --force --timeout=120s",
                    nodeName
            );

            if (!runCommandOnNode(master, drainCmd)) {
                throw new RuntimeException("Drain node thất bại");
            }

            drained = true;


            // 2️⃣ Delete node
            String deleteCmd = "kubectl delete node " + nodeName;

            if (!runCommandOnNode(master, deleteCmd)) {
                throw new RuntimeException("Delete node thất bại");
            }

            deleted = true;


            // 3️⃣ Reset worker
            if (!runCommandOnNode(worker, "sudo kubeadm reset -f")) {
                throw new RuntimeException("Reset worker thất bại");
            }


            // 4️⃣ cleanup CNI
            runCommandOnNode(worker, "sudo rm -rf /etc/cni/net.d");
            runCommandOnNode(worker, "sudo ip link delete cni0 || true");
            runCommandOnNode(worker, "sudo ip link delete flannel.1 || true");


            // 5️⃣ xóa DB
            clusterNodeRepository.delete(worker);


            log.info("Worker {} đã bị xóa khỏi DB", nodeName);


            // 6️⃣ update inventory
            updateInventoryAfterNodeChange(cluster);

            log.info("Inventory đã được cập nhật sau khi remove worker");


        } catch (Exception e) {

            log.error("Remove worker thất bại: {}", e.getMessage());

            try {

                if (drained && !deleted) {

                    log.warn("Rollback: uncordon node {}", nodeName);

                    runCommandOnNode(master, "kubectl uncordon " + nodeName);

                }

            } catch (Exception rollbackError) {

                log.error("Rollback thất bại: {}", rollbackError.getMessage());

            }

            throw new RuntimeException("Remove worker thất bại: " + e.getMessage());
        }
    }

    public void updateInventoryAfterNodeChange(Cluster cluster) {

        try {

            String inventoryPath = uploadInventory(cluster);

            log.info("Inventory đã được cập nhật: {}", inventoryPath);

        } catch (Exception e) {

            log.error("Cập nhật inventory thất bại", e);
            throw new RuntimeException("Không thể cập nhật inventory");
        }
    }
    public boolean runCommandOnNode(ClusterNode node, String command) {

        try {

            JSch jsch = new JSch();
            jsch.addIdentity(privateKeyPath);

            Session session = jsch.getSession(
                    node.getUsername(),
                    node.getIpAddress(),
                    22
            );

            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            channel.setCommand(command);
            channel.setInputStream(null);

            InputStream input = channel.getInputStream();

            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String line;

            while ((line = reader.readLine()) != null) {
                log.info("[{}] {}", node.getName(), line);
            }

            while (!channel.isClosed()) {
                Thread.sleep(200);
            }

            int exitStatus = channel.getExitStatus();

            channel.disconnect();
            session.disconnect();

            return exitStatus == 0;

        } catch (Exception e) {

            log.error("SSH command lỗi: {}", e.getMessage(), e);

            return false;
        }
    }

    private boolean runCommandOnMaster(String command) throws Exception {

        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);

        Session session = jsch.getSession(ansibleUser, ansibleHost, 22);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10000);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);

        InputStream input = channel.getInputStream();
        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line;

        while ((line = reader.readLine()) != null) {
            log.info("[MASTER] {}", line);
        }

        while (!channel.isClosed()) {
            Thread.sleep(300);
        }

        int exitStatus = channel.getExitStatus();

        channel.disconnect();
        session.disconnect();

        return exitStatus == 0;
    }

    private boolean runCommandOnWorker(ClusterNode worker, String command) throws Exception {

        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);

        Session session = jsch.getSession(
                worker.getUsername(),
                worker.getIpAddress(),
                worker.getSshPort()
        );

        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10000);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        InputStream input = channel.getInputStream();
        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line;

        while ((line = reader.readLine()) != null) {
            log.info("[WORKER {}] {}", worker.getName(), line);
        }

        while (!channel.isClosed()) {
            Thread.sleep(300);
        }

        int exitStatus = channel.getExitStatus();

        channel.disconnect();
        session.disconnect();

        return exitStatus == 0;
    }
}
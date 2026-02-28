package rancher.k8s.service.iml;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import com.jcraft.jsch.*;
import org.springframework.stereotype.Service;
import rancher.k8s.dto.ClusterRequest;
import rancher.k8s.service.ClusterService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class ClusterServiceImpl implements ClusterService {
    @Value("${ansible.host}")
    private String ansibleHost;

    @Value("${ansible.user}")
    private String ansibleUser;

    @Value("${ansible.private-key-path}")
    private String privateKeyPath;

    @Value("${kubespray.remote-dir}")
    private String remoteKubesprayDir;

    private static final String OLD_INVENTORY = "mycluster";


    @Override
    public String createCluster(ClusterRequest request) throws Exception {
        String clusterName = sanitizeClusterName(request.getClusterName());
        String inventoryName = clusterName;
        String remoteInventoryPath = remoteKubesprayDir + "/inventory/" + inventoryName;

        log.info("Bắt đầu tạo cluster: {}", inventoryName);

        // 1. Copy inventory cũ sang mới trên remote
        executeRemoteCommand("cp -rfp " + remoteKubesprayDir + "/inventory/" + OLD_INVENTORY + " " + remoteInventoryPath);

        // 2. Generate và upload hosts.yaml mới
        String hostsContent = buildHostsYaml(request);
        uploadStringToRemote(hostsContent, remoteInventoryPath + "/hosts.yaml");

        // 3. Update metallb_ip_range nếu có
        if (request.getMetallbRange() != null && !request.getMetallbRange().isBlank()) {
            String updateMetallb = "sed -i \"s|- .*|- " + request.getMetallbRange() + "|\" " +
                    remoteInventoryPath + "/group_vars/k8s_cluster/addons.yml";
            executeRemoteCommand(updateMetallb);
        }

        // 4. Update cluster_name
        String updateClusterName = "sed -i \"s/^cluster_name: .*/cluster_name: " + clusterName + ".local/\" " +
                remoteInventoryPath + "/group_vars/k8s_cluster/k8s-cluster.yml";
        executeRemoteCommand(updateClusterName);
        /// 5. Chạy ansible-playbook
        String playbookCmd = String.format(
                "cd %s && source venv/bin/activate && " +
                        "ansible-playbook -i inventory/%s/hosts.yaml cluster.yml --become --become-user=root --check --diff -v ",
                remoteKubesprayDir, inventoryName
        );

        String ansibleLog = executeRemoteCommand(playbookCmd);

        return "Cluster " + inventoryName + " đã được khởi tạo.\nLog ngắn:\n" + ansibleLog.substring(0, Math.min(2000, ansibleLog.length())) + "...";

//        return "Copy inventory thành công: " + remoteInventoryPath + " đã được tạo trên máy Ansible.";
    }
    @Override
    public String testSshConnection() throws Exception {
        return executeRemoteCommand("echo 'Test SSH từ Spring Boot trên Windows OK'");
    }

    private String sanitizeClusterName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Tên cluster không hợp lệ");
        return name.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    private String buildHostsYaml(ClusterRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("all:\n  hosts:\n");

        // Masters
        request.getMasterNodes().forEach(n -> {
            sb.append("    ").append(n.getHostname()).append(":\n");
            sb.append("      ansible_host: ").append(n.getIp()).append("\n");
            sb.append("      ip: ").append(n.getIp()).append("\n");
            sb.append("      access_ip: ").append(n.getIp()).append("\n");
        });

        // Workers
        request.getWorkerNodes().forEach(n -> {
            sb.append("    ").append(n.getHostname()).append(":\n");
            sb.append("      ansible_host: ").append(n.getIp()).append("\n");
            sb.append("      ip: ").append(n.getIp()).append("\n");
            sb.append("      access_ip: ").append(n.getIp()).append("\n");
        });

        sb.append("  children:\n");
        sb.append("    kube_control_plane:\n      hosts:\n");
        request.getMasterNodes().forEach(n -> sb.append("        ").append(n.getHostname()).append(":\n"));

        sb.append("    kube_node:\n      hosts:\n");
        request.getWorkerNodes().forEach(n -> sb.append("        ").append(n.getHostname()).append(":\n"));

        sb.append("    etcd:\n      hosts:\n");
        request.getMasterNodes().forEach(n -> sb.append("        ").append(n.getHostname()).append(":\n")); // Chỉ master

        sb.append("    k8s_cluster:\n      children:\n        kube_control_plane:\n        kube_node:\n");

        sb.append("  vars:\n    ansible_user: luanvan\n    ansible_become: true\n");

        return sb.toString();
    }

    private String executeRemoteCommand(String command) throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);

        Session session = jsch.getSession(ansibleUser, ansibleHost, 22);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        InputStream in = channel.getInputStream();
        channel.connect();

        StringBuilder output = new StringBuilder();
        byte[] tmp = new byte[1024];
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                output.append(new String(tmp, 0, i));
            }
            if (channel.isClosed()) break;
            Thread.sleep(1000);
        }

        int exitStatus = channel.getExitStatus();
        channel.disconnect();
        session.disconnect();

        if (exitStatus != 0) {
            throw new RuntimeException("Lệnh thất bại: " + command + "\nExit: " + exitStatus + "\nOutput:\n" + output);
        }

        return output.toString();
    }

    private void uploadStringToRemote(String content, String remotePath) throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);

        Session session = jsch.getSession(ansibleUser, ansibleHost, 22);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();

        try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            channel.put(is, remotePath);
        }

        channel.disconnect();
        session.disconnect();
    }
}

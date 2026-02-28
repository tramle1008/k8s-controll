package rancher.k8s.service;

import rancher.k8s.dto.ClusterRequest;

public interface ClusterService {

    /**
     * Tạo cluster mới từ request JSON
     * @return Tên inventory mới
     */
    String createCluster(ClusterRequest request) throws Exception;

    /**
     * Test kết nối SSH đến máy Ansible
     * @return Kết quả test
     */
    String testSshConnection() throws Exception;
}
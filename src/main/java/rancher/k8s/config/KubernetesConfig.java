package rancher.k8s.config;  // Quan trọng: package phải là con hoặc cùng cấp với package main

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class KubernetesConfig {
    // Nếu bạn muốn hardcode cố định:
     private static final String KUBE_CONFIG_PATH = "E:\\.kube\\config";

    @Bean
    public KubernetesClient kubernetesClient() {
        try {
            // Kiểm tra file có tồn tại không (giúp debug sớm)
            java.nio.file.Path path = Paths.get(KUBE_CONFIG_PATH);
            if (!Files.exists(path)) {
                throw new IOException("File kubeconfig không tồn tại tại đường dẫn: " + KUBE_CONFIG_PATH);
            }
            // Đọc toàn bộ nội dung file kubeconfig thành String
           String kubeconfigContent = Files.readString(path);
            // Parse kubeconfig từ nội dung String
            Config config = Config.fromKubeconfig(kubeconfigContent);
            // Build client
            KubernetesClient client = new KubernetesClientBuilder()
                    .withConfig(config)
                    .build();
            // In thông tin debug
            System.out.println(">>> Fabric8 Kubernetes Client khởi tạo thành công!");
            System.out.println(">>> API Server: " + client.getConfiguration().getMasterUrl());
            System.out.println(">>> Namespace mặc định: " + client.getConfiguration().getNamespace());
            System.out.println(">>> Đường dẫn kubeconfig sử dụng: " + KUBE_CONFIG_PATH);
            return client;
        } catch (IOException e) {
            throw new RuntimeException("Không đọc được file kubeconfig tại: " + KUBE_CONFIG_PATH, e);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi khởi tạo KubernetesClient từ file config: " + e.getMessage(), e);
        }
    }
}
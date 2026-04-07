package infra.k8s.dto.ssh;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ansible.control")
@Data
public class SshConfig {
    private String host;
    private String user;
    private String privateKeyPath;
}
package infra.k8s.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ssh.encryption")
@Getter
@Setter
public class EncryptionProperties {
    private String secret;
}
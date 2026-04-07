package infra.k8s.Context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "registry")
@Data
public class RegistryProperties {
    private String url;
    private String host;
}
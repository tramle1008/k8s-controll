package infra.k8s.dto.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeSshInfo {
    private String nodeName;
    private String host;
    private String username;
    private Integer sshPort;
    private String password;
    private String role;
}
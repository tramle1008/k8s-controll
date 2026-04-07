package infra.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// setup key
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SshDto {
    private String method;     // "password" hoặc "key" (hiện tại chủ yếu password)
    private String password;   // chỉ dùng để deploy key lần đầu
    private String publicKey;  // hiện tại frontend gửi rỗng → backend sẽ generate
    private Integer sshPort = 22;
}
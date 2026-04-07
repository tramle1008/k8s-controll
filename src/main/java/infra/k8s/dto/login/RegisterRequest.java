package infra.k8s.dto.login;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String role;      // "USER" hoặc "ADMIN"
    private Long clusterId;   // chỉ dùng khi role = USER
}
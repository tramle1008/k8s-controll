package infra.k8s.JwtService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import infra.k8s.Context.UserRole;
import infra.k8s.dto.login.AuthResponse;
import infra.k8s.dto.login.LoginRequest;
import infra.k8s.dto.login.RegisterRequest;
import infra.k8s.module.Cluster;
import infra.k8s.module.User;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ClusterRepository clusterRepository;

    // ================= LOGIN =================
    public AuthResponse authenticate(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .role(user.getUserRole().name())
                .clusterId(user.getCluster() != null ? user.getCluster().getId() : null)
                .build();
    }

    // ================= REGISTER =================
    public AuthResponse register(RegisterRequest request) {

        // check username
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        // parse role
        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Invalid role");
        }

        Cluster cluster = null;

        // nếu là USER → bắt buộc có cluster
        if (role == UserRole.USER) {
            if (request.getClusterId() == null) {
                throw new RuntimeException("User must belong to a cluster");
            }

            cluster = clusterRepository.findById(request.getClusterId())
                    .orElseThrow(() -> new RuntimeException("Cluster not found"));
        }

        // tạo user
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .userRole(role)
                .cluster(cluster)   // ADMIN = null
                .build();

        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .role(user.getUserRole().name())
                .clusterId(user.getCluster() != null ? user.getCluster().getId() : null)
                .build();
    }
}
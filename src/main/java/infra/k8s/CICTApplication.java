package infra.k8s;

import infra.k8s.JwtService.AuthenticationService;
import infra.k8s.dto.login.RegisterRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class CICTApplication {

	private final AuthenticationService authenticationService;

	public CICTApplication(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	public static void main(String[] args) {
		SpringApplication.run(CICTApplication.class, args);
	}

	@Bean
	public CommandLineRunner initAdmin(AuthenticationService authenticationService, PasswordEncoder passwordEncoder) {
		return args -> {
			RegisterRequest adminRequest = new RegisterRequest();
			adminRequest.setUsername("admin");
			adminRequest.setPassword("123456");
			adminRequest.setRole("ADMIN");

			try {
				authenticationService.register(adminRequest);
				System.out.println("Admin user created!");
			} catch (RuntimeException e) {
				System.out.println("Admin already exists: " + e.getMessage());
			}
		};
	}
}

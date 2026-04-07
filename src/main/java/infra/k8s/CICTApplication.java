package infra.k8s;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "rancher.k8s")
@EnableScheduling
public class CICTApplication {

	public static void main(String[] args) {
		SpringApplication.run(CICTApplication.class, args);
	}

}

package rancher.k8s;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "rancher.k8s")
public class RancherApplication {

	public static void main(String[] args) {
		SpringApplication.run(RancherApplication.class, args);
	}

}

package infra.k8s.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import infra.k8s.module.User;


import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
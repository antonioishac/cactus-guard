package br.com.cactus.guard.config;

import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Criando usuário admin inicial...");

            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("ADMIN")
                            .description("Administrador do sistema")
                            .build()));

            User admin = User.builder()
                    .username("admin")
                    .email("admin@cactusguard.com")
                    .password(passwordEncoder.encode("admin123"))
                    .name("Administrador")
                    .active(true)
                    .roles(Set.of(adminRole))
                    .build();

            userRepository.save(admin);
            log.info("Usuário admin criado. Username: admin | Password: admin123");
        }
    }
}

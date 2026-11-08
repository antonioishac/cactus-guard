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

    /** Sem default: em producao o admin so e criado se ADMIN_BOOTSTRAP_PASSWORD for informada. */
    @org.springframework.beans.factory.annotation.Value("${security.admin-bootstrap.password:}")
    private String bootstrapPassword;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            if (bootstrapPassword == null || bootstrapPassword.length() < 8) {
                log.warn("Nenhum usuario existente e ADMIN_BOOTSTRAP_PASSWORD nao informada: admin inicial NAO criado");
                return;
            }
            log.info("Criando usuário admin inicial...");

            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("ADMIN")
                            .description("Administrador do sistema")
                            .build()));

            User admin = User.builder()
                    .username("admin")
                    .email("admin@cactusguard.com")
                    .password(passwordEncoder.encode(bootstrapPassword))
                    .name("Administrador")
                    .active(true)
                    .roles(Set.of(adminRole))
                    .build();

            userRepository.save(admin);
            log.info("Usuario admin inicial criado (username: admin). Troque a senha no primeiro acesso.");
        }
    }
}

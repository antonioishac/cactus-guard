package br.com.cactus.guard.user.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.repository.RefreshTokenRepository;
import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.exception.NotFoundException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.user.dto.PasswordResetRequest;
import br.com.cactus.guard.user.dto.PasswordResetResponse;
import br.com.cactus.guard.user.dto.UserCreateRequest;
import br.com.cactus.guard.user.dto.UserResponse;
import br.com.cactus.guard.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*?";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentUserService currentUser;
    private final br.com.cactus.guard.tenant.service.TenantDirectory tenantDirectory;

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        // username e unico GLOBALMENTE (identifica o tenant no login); a busca precisa enxergar todos os tenants
        if (tenantDirectory.findTenantIdByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Set<Role> roles = new HashSet<>();
        if (request.getRoles() != null) {
            for (String roleName : request.getRoles()) {
                roleRepository.findByName(roleName).ifPresent(roles::add);
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .active(true)
                .roles(roles)
                .build();

        user = userRepository.save(user);
        auditService.log("USER_CREATED", "USER", user.getId(), null, Map.of(
                "username", user.getUsername(),
                "roles", roleNames(user)));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        String email = request.email().trim();
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessException("EMAIL_IN_USE", "E-mail já cadastrado");
        }
        Set<Role> roles = new HashSet<>();
        for (String roleName : request.roles()) {
            roles.add(roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalArgumentException("roles: perfil inexistente")));
        }
        boolean selfRemovingAdmin = currentUser.user().map(u -> u.getId().equals(id)).orElse(false)
                && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()))
                && roles.stream().noneMatch(r -> "ADMIN".equals(r.getName()));
        if (selfRemovingAdmin) {
            throw new BusinessException("Não é permitido remover o próprio perfil ADMIN");
        }
        Map<String, Object> oldValue = Map.of("name", user.getName(), "roles", roleNames(user));
        user.setName(request.name().trim());
        user.setEmail(email);
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        user = userRepository.save(user);
        auditService.log("USER_UPDATED", "USER", user.getId(), oldValue,
                Map.of("name", user.getName(), "roles", roleNames(user)));
        return toResponse(user);
    }

    @Transactional
    public UserResponse deactivate(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setActive(false);
        user = userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        auditService.log("USER_DEACTIVATED", "USER", user.getId(), Map.of("active", true), Map.of("active", false));
        return toResponse(user);
    }

    @Transactional
    public UserResponse activate(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        user.setActive(true);
        user = userRepository.save(user);
        auditService.log("USER_ACTIVATED", "USER", user.getId(), Map.of("active", false), Map.of("active", true));
        return toResponse(user);
    }

    /** Reset por ADMIN: troca a senha, revoga todos os refresh tokens e audita (sem a senha). */
    @Transactional
    public PasswordResetResponse resetPassword(UUID id, PasswordResetRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        boolean generated = request == null || request.newPassword() == null || request.newPassword().isBlank();
        String raw = generated ? generatePassword() : request.newPassword();
        user.setPassword(passwordEncoder.encode(raw));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        auditService.log("USER_PASSWORD_RESET", "USER", user.getId(), null, Map.of("generated", generated));
        return new PasswordResetResponse(user.getId(), generated ? raw : null, true);
    }

    /** 16 caracteres com maiuscula, minuscula, digito e simbolo (SecureRandom; sem caracteres ambiguos). */
    public static String generatePassword() {
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        List<Character> chars = new ArrayList<>();
        for (String set : new String[]{UPPER, LOWER, DIGITS, SYMBOLS}) {
            chars.add(set.charAt(RANDOM.nextInt(set.length())));
        }
        while (chars.size() < 16) {
            chars.add(all.charAt(RANDOM.nextInt(all.length())));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static List<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).sorted().collect(Collectors.toList());
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .active(user.isActive())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}

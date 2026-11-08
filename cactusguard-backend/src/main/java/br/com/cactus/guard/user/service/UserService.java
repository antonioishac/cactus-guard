package br.com.cactus.guard.user.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.repository.RefreshTokenRepository;
import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.user.dto.UserCreateRequest;
import br.com.cactus.guard.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
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
                "roles", user.getRoles().stream().map(Role::getName).sorted().collect(Collectors.toList())));
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
    public UserResponse deactivate(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setActive(false);
        user = userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        auditService.log("USER_DEACTIVATED", "USER", user.getId(), Map.of("active", true), Map.of("active", false));
        return toResponse(user);
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

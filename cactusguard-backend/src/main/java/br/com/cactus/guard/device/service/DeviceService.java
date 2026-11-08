package br.com.cactus.guard.device.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.exception.NotFoundException;
import br.com.cactus.guard.device.dto.DeviceRegisterRequest;
import br.com.cactus.guard.device.dto.DeviceResponse;
import br.com.cactus.guard.device.dto.PushPreferencesDto;
import br.com.cactus.guard.device.entity.PushPreference;
import br.com.cactus.guard.device.entity.UserDevice;
import br.com.cactus.guard.device.repository.PushPreferenceRepository;
import br.com.cactus.guard.device.repository.UserDeviceRepository;
import br.com.cactus.guard.integration.push.PushSenderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;


/** Registro de aparelhos e preferencias de push. O token nunca e logado em claro nem devolvido. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository deviceRepository;
    private final PushPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** Upsert por token; se pertencia a outro usuario, transfere e audita. Retorna (resposta, criado?). */
    @Transactional
    public Registration register(String username, DeviceRegisterRequest req) {
        User user = currentUser(username);
        Instant now = Instant.now();
        UserDevice device = deviceRepository.findByToken(req.token()).orElse(null);
        boolean created = device == null;

        if (created) {
            device = UserDevice.builder().user(user).token(req.token()).lastSeenAt(now).build();
        } else if (!device.getUser().getId().equals(user.getId())) {
            Map<String, Object> old = new HashMap<>();
            old.put("userId", device.getUser().getId().toString());
            Map<String, Object> neu = new HashMap<>();
            neu.put("userId", user.getId().toString());
            device.setUser(user);
            auditService.log("DEVICE_TOKEN_TRANSFERRED", "UserDevice", device.getId(), old, neu);
            log.info("Token de aparelho transferido entre usuarios: {}", PushSenderPort.mask(req.token()));
        }
        device.setPlatform(req.platform());
        device.setAppVersion(req.appVersion());
        device.setLastSeenAt(now);
        device.setRevokedAt(null);
        device = deviceRepository.save(device);

        if (created) {
            auditService.log("DEVICE_REGISTERED", "UserDevice", device.getId(), null,
                    Map.of("platform", req.platform().name()));
        }
        return new Registration(toResponse(device), created);
    }

    /** Revoga (logout do aparelho) somente se for do usuario; caso contrario 404 (nao revela existencia). */
    @Transactional
    public void revoke(String username, String token) {
        User user = currentUser(username);
        UserDevice device = deviceRepository.findByToken(token)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Aparelho nao encontrado"));
        if (device.getRevokedAt() == null) {
            device.setRevokedAt(Instant.now());
            auditService.log("DEVICE_REVOKED", "UserDevice", device.getId(), null,
                    Map.of("platform", device.getPlatform().name()));
        }
    }

    /** Remove token que o provedor declarou invalido (UNREGISTERED). */
    @Transactional
    public void removeInvalidToken(String token) {
        deviceRepository.deleteByTokenValue(token);
    }

    @Transactional(readOnly = true)
    public PushPreferencesDto getPreferences(String username) {
        User user = currentUser(username);
        return preferenceRepository.findById(user.getId())
                .map(p -> new PushPreferencesDto(p.isCriticalAlerts(), p.isDeadlines()))
                .orElse(new PushPreferencesDto(true, true));
    }

    @Transactional
    public PushPreferencesDto updatePreferences(String username, PushPreferencesDto dto) {
        User user = currentUser(username);
        PushPreference pref = preferenceRepository.findById(user.getId())
                .orElseGet(() -> PushPreference.builder().userId(user.getId()).build());
        pref.setCriticalAlerts(dto.criticalAlerts());
        pref.setDeadlines(dto.deadlines());
        pref.setUpdatedAt(Instant.now());
        preferenceRepository.save(pref);
        return dto;
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Usuario autenticado nao encontrado"));
    }

    private static DeviceResponse toResponse(UserDevice d) {
        return new DeviceResponse(d.getId(), d.getPlatform(), d.getAppVersion(), d.getCreatedAt(), d.getLastSeenAt());
    }

    public record Registration(DeviceResponse device, boolean created) {
    }
}

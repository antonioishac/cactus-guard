package br.com.cactus.guard.device.service;

import br.com.cactus.guard.device.entity.PushPreference;
import br.com.cactus.guard.device.entity.UserDevice;
import br.com.cactus.guard.device.repository.PushPreferenceRepository;
import br.com.cactus.guard.device.repository.UserDeviceRepository;
import br.com.cactus.guard.integration.push.PushMessage;
import br.com.cactus.guard.integration.push.PushResult;
import br.com.cactus.guard.integration.push.PushSenderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** Resolve destinatarios (usuarios/roles), aplica preferencias, envia e remove tokens invalidos. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatcher {

    public enum Kind { CRITICAL_ALERTS, DEADLINES }

    private final UserDeviceRepository deviceRepository;
    private final PushPreferenceRepository preferenceRepository;
    private final PushSenderPort sender;
    private final DeviceService deviceService;

    /** Envia a todos os aparelhos ativos de {@code userIds} e/ou usuarios com {@code roles}, exceto {@code exclude}. */
    public int dispatch(Collection<UUID> userIds, Collection<String> roles, Collection<UUID> exclude,
                        PushMessage message, Kind kind) {
        Map<UUID, UserDevice> devices = new LinkedHashMap<>();
        if (userIds != null && !userIds.isEmpty()) {
            deviceRepository.findActiveByUserIds(userIds).forEach(d -> devices.put(d.getId(), d));
        }
        if (roles != null && !roles.isEmpty()) {
            deviceRepository.findActiveByRoles(roles).forEach(d -> devices.put(d.getId(), d));
        }
        Set<UUID> excluded = exclude == null ? Set.of() : new HashSet<>(exclude);
        List<UserDevice> targets = devices.values().stream()
                .filter(d -> !excluded.contains(d.getUserId())).toList();
        if (targets.isEmpty()) {
            return 0;
        }

        Set<UUID> ids = targets.stream().map(UserDevice::getUserId).collect(Collectors.toSet());
        Set<UUID> optedOut = preferenceRepository.findAllById(ids).stream()
                .filter(p -> kind == Kind.CRITICAL_ALERTS ? !p.isCriticalAlerts() : !p.isDeadlines())
                .map(PushPreference::getUserId).collect(Collectors.toSet());

        int sent = 0;
        for (UserDevice d : targets) {
            if (optedOut.contains(d.getUserId())) {
                continue;
            }
            try {
                PushResult r = sender.send(d.getToken(), message);
                if (r == PushResult.SENT) {
                    sent++;
                } else if (r == PushResult.INVALID_TOKEN) {
                    deviceService.removeInvalidToken(d.getToken());
                }
            } catch (RuntimeException e) {
                log.warn("Falha ao enviar push type={} token={}: {}", message.type(),
                        PushSenderPort.mask(d.getToken()), e.getClass().getSimpleName());
            }
        }
        log.debug("Push {} despachado: alvos={} enviados={}", message.type(), targets.size(), sent);
        return sent;
    }
}

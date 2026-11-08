package br.com.cactus.guard.device.push;

import br.com.cactus.guard.common.event.CasePendingApprovalEvent;
import br.com.cactus.guard.common.event.CriticalAlertCreatedEvent;
import br.com.cactus.guard.device.service.PushDispatcher;
import br.com.cactus.guard.integration.push.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Gatilhos de push por evento de aplicacao: apos o commit, assincrono, e nunca propaga erro ao publicador. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushEventListener {

    private final PushDispatcher dispatcher;
    private final PushRecipientResolver resolver;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCriticalAlert(CriticalAlertCreatedEvent event) {
        try {
            PushMessage msg = PushMessage.criticalAlert(event.alertId());
            Optional<UUID> assignee = resolver.alertAssignee(event.alertId());
            if (assignee.isPresent()) {
                dispatcher.dispatch(List.of(assignee.get()), null, null, msg, PushDispatcher.Kind.CRITICAL_ALERTS);
            } else {
                dispatcher.dispatch(null, Set.of("ANALYST", "SUPERVISOR"), null, msg, PushDispatcher.Kind.CRITICAL_ALERTS);
            }
        } catch (RuntimeException e) {
            log.warn("Push de alerta critico nao enviado: {}", e.getClass().getSimpleName());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCasePendingApproval(CasePendingApprovalEvent event) {
        try {
            Set<UUID> exclude = event.decidedById() == null ? Set.of() : Set.of(event.decidedById());
            dispatcher.dispatch(null, Set.of("SUPERVISOR"), exclude,
                    PushMessage.casePendingApproval(event.caseId()), PushDispatcher.Kind.CRITICAL_ALERTS);
        } catch (RuntimeException e) {
            log.warn("Push de aprovacao pendente nao enviado: {}", e.getClass().getSimpleName());
        }
    }
}

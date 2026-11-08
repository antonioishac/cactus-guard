package br.com.cactus.guard.device.push;

import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.device.service.PushDispatcher;
import br.com.cactus.guard.integration.push.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Job diario: avisa responsavel + SUPERVISORes de cases com prazo de analise em ate 2 dias uteis
 * (inclui vencidos) ou prazo de comunicacao COAF ate hoje. Conteudo generico (type SLA + id).
 * Teto de cases por execucao para evitar rajada de notificacoes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseDeadlinePushJob {

    static final int MAX_CASES_PER_RUN = 200;

    private final PushRecipientResolver resolver;
    private final PushDispatcher dispatcher;
    private final BusinessCalendar calendar;

    @Scheduled(cron = "${cactus.push.sla-cron:0 0 8 * * *}", zone = "America/Sao_Paulo")
    public void run() {
        try {
            runOnce(LocalDate.now(BusinessCalendar.BRAZIL));
        } catch (RuntimeException e) {
            log.warn("Job de push de prazos falhou: {}", e.getClass().getSimpleName());
        }
    }

    /** Visivel para teste. Retorna quantos cases foram notificados. */
    int runOnce(LocalDate today) {
        Instant analysisLimit = BusinessCalendar.endOfDay(calendar.nextBusinessDay(calendar.nextBusinessDay(today)));
        Instant commLimit = BusinessCalendar.endOfDay(today);
        List<PushRecipientResolver.DueCase> due = resolver.dueCases(analysisLimit, commLimit, MAX_CASES_PER_RUN);
        for (PushRecipientResolver.DueCase c : due) {
            List<UUID> users = c.assigneeId() == null ? List.of() : List.of(c.assigneeId());
            dispatcher.dispatch(users, Set.of("SUPERVISOR"), null,
                    PushMessage.slaDeadline(c.caseId()), PushDispatcher.Kind.DEADLINES);
        }
        log.info("Push de prazos: {} case(s) com prazo proximo", due.size());
        return due.size();
    }
}

package br.com.cactus.guard.transaction.service;

import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.alert.service.AlertCreationService.AlertDraft;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.party.entity.Party;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de tentativa de movimentacao de parte com ativos indisponiveis (Lei 13.810/2019,
 * Res. BCB 44/2020). Roda em transacao propria (REQUIRES_NEW) para que o alerta CRITICAL e a
 * auditoria sobrevivam ao rollback da transacao que rejeita a operacao.
 */
@Service
@RequiredArgsConstructor
public class AssetFreezeGuard {

    private final AlertCreationService alertCreation;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerBlockedAttempt(Party party, String type, BigDecimal amount, String direction) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("assetFreezeReference", party.getAssetFreezeReference());
        evidence.put("attemptedType", type);
        evidence.put("attemptedAmount", amount);
        evidence.put("attemptedDirection", direction);

        // no maximo um alerta por parte e dia; cada tentativa e auditada
        alertCreation.createIfAbsent(new AlertDraft(party, null, "SANCTION", "CRITICAL",
                "Tentativa de movimentação de parte com ativos indisponíveis",
                "Operação rejeitada: parte com indisponibilidade de ativos ativa (Lei 13.810/2019)",
                "ASSET_FREEZE_ATTEMPT", "ASSET_FREEZE_ATTEMPT:" + party.getId() + ":" + LocalDate.now(),
                amount, null, evidence, List.of()));
        AuditPayload.log(auditService, "ASSET_FREEZE_TRANSACTION_REJECTED", "PARTY", party.getId(), null, evidence);
    }
}

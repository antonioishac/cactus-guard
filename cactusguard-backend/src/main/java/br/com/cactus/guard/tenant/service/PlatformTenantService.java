package br.com.cactus.guard.tenant.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.exception.NotFoundException;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.dto.TenantCreateRequest;
import br.com.cactus.guard.tenant.dto.TenantProvisionedResponse;
import br.com.cactus.guard.tenant.dto.TenantResponse;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import br.com.cactus.guard.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Operacoes de PLATAFORMA sobre tenants (role PLATFORM_ADMIN). Nao le nem escreve dado de negocio de
 * tenant algum: o unico trabalho dentro de um tenant e o seu proprio provisionamento (regras-modelo + primeiro
 * admin), feito em transacao propria no contexto do tenant novo. Toda acao e auditada (no tenant 'platform',
 * onde vivem os operadores, e no proprio tenant provisionado).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformTenantService {

    private final TenantRepository tenantRepository;
    private final TenantDirectory tenantDirectory;
    private final TenantAccessService tenantAccess;
    private final TransactionTemplate tx;
    private final MonitoringRuleService ruleService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Sem @Transactional: cada etapa tem a sua transacao (a troca de tenant exige nova sessao/conexao). */
    public TenantProvisionedResponse provision(TenantCreateRequest req) {
        String cnpjHash = DocumentHash.ofPlatform(req.cnpj());
        if (tenantRepository.existsByCode(req.code())) {
            throw new BusinessException("TENANT_CODE_IN_USE", "Ja existe um tenant com este codigo");
        }
        if (tenantRepository.existsByCnpjHash(cnpjHash)) {
            throw new BusinessException("TENANT_CNPJ_IN_USE", "Ja existe um tenant com este CNPJ");
        }
        if (tenantDirectory.findTenantIdByUsername(req.adminUsername()).isPresent()) {
            throw new BusinessException("USERNAME_IN_USE", "Username ja existe");
        }

        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tx.execute(s -> tenantRepository.save(Tenant.builder()
                .id(tenantId)
                .code(req.code())
                .name(req.name().trim())
                .cnpj(req.cnpj())
                .cnpjHash(cnpjHash)
                .regulatorySegment(req.regulatorySegment())
                .build()));

        String temporaryPassword = UserService.generatePassword();
        int rulesCopied;
        try {
            rulesCopied = TenantContext.callAs(tenantId, () -> setUpTenant(req, temporaryPassword));
        } catch (RuntimeException e) {
            // a etapa dentro do tenant e uma unica transacao (ja desfeita); remove o tenant vazio para permitir nova tentativa
            tx.executeWithoutResult(s -> tenantRepository.deleteById(tenantId));
            throw e;
        }

        auditService.log("TENANT_PROVISIONED", "TENANT", tenantId, null,
                Map.of("code", tenant.getCode(), "segment", tenant.getRegulatorySegment(), "rulesCopied", rulesCopied));
        log.info("Tenant provisionado: code={}", tenant.getCode());
        return new TenantProvisionedResponse(TenantResponse.from(tenant), req.adminUsername(), temporaryPassword, rulesCopied);
    }

    /** Uma unica transacao no tenant novo: regras-modelo + primeiro admin + auditoria (tudo ou nada). */
    private int setUpTenant(TenantCreateRequest req, String temporaryPassword) {
        Role admin = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ADMIN inexistente"));
        Integer copied = tx.execute(s -> {
            int rules = ruleService.provisionFromTemplates();
            User user = userRepository.save(User.builder()
                    .username(req.adminUsername())
                    .email(req.adminEmail())
                    .password(passwordEncoder.encode(temporaryPassword))
                    .name(req.adminName())
                    .active(true)
                    .roles(Set.of(admin))
                    .build());
            auditService.log("TENANT_PROVISIONED", "TENANT", TenantContext.require(), null,
                    Map.of("firstAdminUserId", user.getId().toString(), "rulesCopied", rules));
            return rules;
        });
        return copied == null ? 0 : copied;
    }

    public Page<TenantResponse> list(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(TenantResponse::from);
    }

    public TenantResponse get(UUID id) {
        return TenantResponse.from(find(id));
    }

    public TenantResponse suspend(UUID id, String reason) {
        String just = Justifications.require(reason, "suspender tenant");
        Tenant t = find(id);
        if (TenantContext.PLATFORM_TENANT_ID.equals(id)) {
            throw new BusinessException("O tenant da plataforma nao pode ser suspenso");
        }
        return changeStatus(t, Tenant.SUSPENDED, "TENANT_SUSPENDED", just);
    }

    public TenantResponse reactivate(UUID id, String reason) {
        String just = Justifications.require(reason, "reativar tenant");
        return changeStatus(find(id), Tenant.ACTIVE, "TENANT_REACTIVATED", just);
    }

    private TenantResponse changeStatus(Tenant t, String newStatus, String action, String reason) {
        String old = t.getStatus();
        t.setStatus(newStatus);
        Tenant saved = tx.execute(s -> tenantRepository.save(t));
        tenantAccess.invalidate(t.getId());
        auditService.log(action, "TENANT", t.getId(), Map.of("status", old),
                Map.of("status", newStatus, "code", t.getCode(), "reason", reason));
        return TenantResponse.from(saved);
    }

    private Tenant find(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new NotFoundException("Tenant nao encontrado"));
    }
}

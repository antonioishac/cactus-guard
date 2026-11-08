package br.com.cactus.guard.report.service;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.report.dto.ReportGenerateRequest;
import br.com.cactus.guard.report.dto.ReportJobResponse;
import br.com.cactus.guard.report.entity.ReportDefinition;
import br.com.cactus.guard.report.entity.ReportJob;
import br.com.cactus.guard.report.repository.ReportDefinitionRepository;
import br.com.cactus.guard.report.repository.ReportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    @Value("${reports.output-dir:reports}")
    private String outputDirName;

    private final ReportDefinitionRepository definitionRepository;
    private final ReportJobRepository jobRepository;
    private final ReportGenerationService generationService;
    private final ExcelReportService excelReportService;
    private final UserRepository userRepository;
    private final br.com.cactus.guard.audit.service.AuditService auditService;

    @Transactional(readOnly = true)
    public List<ReportDefinition> findAllDefinitions() {
        return definitionRepository.findByActiveTrue();
    }

    @Transactional
    public ReportJobResponse generate(String reportCode, ReportGenerateRequest request) {
        ReportDefinition def = definitionRepository.findByCode(reportCode)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportCode));

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);

        ReportJob job = ReportJob.builder()
                .reportCode(reportCode)
                .requestedBy(user)
                .status("PENDING")
                .format(request.getFormat())
                .parameters(request.getParameters())
                .build();

        ReportJob saved = jobRepository.save(job);
        // Resposta montada ANTES de disparar o processamento assincrono (que muta a mesma instancia
        // em outra thread) e disparo so apos o commit, para o worker nao enxergar/gravar um job
        // ainda nao persistido.
        ReportJobResponse response = toResponse(saved);
        Runnable dispatch = () -> {
            if ("EXCEL".equalsIgnoreCase(request.getFormat())) {
                excelReportService.generateExcelAsync(saved);
            } else {
                generationService.generateAsync(saved);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
        return response;
    }

    @Transactional(readOnly = true)
    public ReportJobResponse findJob(UUID jobId) {
        ReportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        assertCanAccess(job);
        return toResponse(job);
    }

    /** Arquivo validado de um job: so o solicitante, SUPERVISOR ou ADMIN; dentro do diretorio de relatorios. */
    public record ReportFile(Path path, String format) { }

    @Transactional
    public ReportFile resolveDownload(UUID jobId) {
        ReportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        assertCanAccess(job);
        if (job.getFilePath() == null) {
            throw new IllegalArgumentException("Report file not available");
        }
        try {
            Path base = Path.of(outputDirName).toRealPath();
            Path file = Path.of(job.getFilePath()).toRealPath();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) {
                log.warn("Download de relatorio bloqueado: caminho fora do diretorio permitido (job {})", jobId);
                throw new AccessDeniedException("Acesso negado");
            }
            auditService.log("REPORT_DOWNLOAD", "REPORT_JOB", jobId, null, java.util.Map.of("reportCode", job.getReportCode()));
            return new ReportFile(file, job.getFormat());
        } catch (IOException e) {
            throw new IllegalArgumentException("Report file not available");
        }
    }

    private void assertCanAccess(ReportJob job) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Acesso negado");
        }
        boolean privileged = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPERVISOR"));
        boolean owner = job.getRequestedBy() != null
                && job.getRequestedBy().getUsername().equals(auth.getName());
        if (!privileged && !owner) {
            throw new AccessDeniedException("Acesso negado");
        }
    }

    @Transactional(readOnly = true)
    public Page<ReportJobResponse> findMyJobs(Pageable pageable) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return Page.empty();
        return jobRepository.findByRequestedById(user.getId(), pageable).map(this::toResponse);
    }

    private ReportJobResponse toResponse(ReportJob job) {
        return ReportJobResponse.builder()
                .id(job.getId())
                .reportCode(job.getReportCode())
                .status(job.getStatus())
                .format(job.getFormat())
                .parameters(job.getParameters())
                .downloadable(job.getFilePath() != null)
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDefinitionRepository definitionRepository;
    private final ReportJobRepository jobRepository;
    private final ReportGenerationService generationService;

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

        job = jobRepository.save(job);
        if ("EXCEL".equalsIgnoreCase(request.getFormat())) {
            excelReportService.generateExcelAsync(job);
        } else {
            generationService.generateAsync(job);
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public ReportJobResponse findJob(UUID jobId) {
        ReportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        return toResponse(job);
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
                .filePath(job.getFilePath())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}

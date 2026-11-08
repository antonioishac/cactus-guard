package br.com.cactus.guard.report.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.report.dto.ReportGenerateRequest;
import br.com.cactus.guard.report.dto.ReportJobResponse;
import br.com.cactus.guard.report.entity.ReportDefinition;
import br.com.cactus.guard.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<List<ReportDefinition>> findAll() {
        return ResponseEntity.ok(reportService.findAllDefinitions());
    }

    @PostMapping("/{code}/generate")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<ReportJobResponse> generate(
            @PathVariable String code,
            @Valid @RequestBody ReportGenerateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reportService.generate(code, request));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<ReportJobResponse> findJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(reportService.findJob(jobId));
    }

    @GetMapping("/jobs/{jobId}/download")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        ReportService.ReportFile file = reportService.resolveDownload(jobId);
        Resource resource = new FileSystemResource(file.path());
        String contentType = "EXCEL".equalsIgnoreCase(file.format())
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/jobs/me")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Page<ReportJobResponse>> findMyJobs(Pageable pageable) {
        return ResponseEntity.ok(reportService.findMyJobs(pageable));
    }
}

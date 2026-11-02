package br.com.cactus.guard.report.controller;

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
    public ResponseEntity<List<ReportDefinition>> findAll() {
        return ResponseEntity.ok(reportService.findAllDefinitions());
    }

    @PostMapping("/{code}/generate")
    public ResponseEntity<ReportJobResponse> generate(
            @PathVariable String code,
            @Valid @RequestBody ReportGenerateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reportService.generate(code, request));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ReportJobResponse> findJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(reportService.findJob(jobId));
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        ReportJobResponse job = reportService.findJob(jobId);
        if (job.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(job.getFilePath());
        String contentType = job.getFormat().equalsIgnoreCase("EXCEL") 
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
        return ResponseEntity.ok(reportService.findMyJobs(pageable));
    }
}

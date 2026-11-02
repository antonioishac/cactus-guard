package br.com.cactus.guard.report.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ReportJobResponse {
    private UUID id;
    private String reportCode;
    private String status;
    private String format;
    private Map<String, Object> parameters;
    private String filePath;
    private Instant createdAt;
    private Instant completedAt;
}

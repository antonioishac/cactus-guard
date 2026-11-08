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
    /** Indica se ha arquivo para download. O caminho no servidor nunca e exposto. */
    private boolean downloadable;
    private Instant createdAt;
    private Instant completedAt;
}

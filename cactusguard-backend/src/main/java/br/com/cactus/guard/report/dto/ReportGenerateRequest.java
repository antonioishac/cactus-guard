package br.com.cactus.guard.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class ReportGenerateRequest {
    @NotBlank
    private String format; // PDF or EXCEL
    private Map<String, Object> parameters;
}

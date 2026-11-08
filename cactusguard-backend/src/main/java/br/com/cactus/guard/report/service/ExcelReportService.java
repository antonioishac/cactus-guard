package br.com.cactus.guard.report.service;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.report.entity.ReportJob;
import br.com.cactus.guard.report.repository.ReportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelReportService {

    private final ReportJobRepository jobRepository;
    private final AlertRepository alertRepository;

    @org.springframework.beans.factory.annotation.Value("${reports.output-dir:reports}")
    private String outputDirName;

    @Async
    public void generateExcelAsync(ReportJob job) {
        try {
            job.setStatus("PROCESSING");
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            Path outputDir = Path.of(outputDirName);
            Files.createDirectories(outputDir);
            String fileName = job.getReportCode() + "_" + job.getId() + ".xlsx";
            Path filePath = outputDir.resolve(fileName);

            generateExcel(job, filePath);

            job.setStatus("COMPLETED");
            job.setFilePath(filePath.toString());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            log.info("Excel report generated: {}", filePath);
        } catch (Exception e) {
            log.error("Excel generation failed", e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private void generateExcel(ReportJob job, Path filePath) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Relatório");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Tipo");
            header.createCell(2).setCellValue("Severidade");
            header.createCell(3).setCellValue("Status");
            header.createCell(4).setCellValue("Título");
            header.createCell(5).setCellValue("Criado em");

            if ("ALERTS_SUMMARY".equals(job.getReportCode())) {
                List<Alert> alerts = alertRepository.findAll(Pageable.unpaged()).getContent();
                int rowNum = 1;
                for (Alert alert : alerts) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(alert.getId().toString());
                    row.createCell(1).setCellValue(alert.getType());
                    row.createCell(2).setCellValue(alert.getSeverity());
                    row.createCell(3).setCellValue(alert.getStatus());
                    row.createCell(4).setCellValue(alert.getTitle());
                    row.createCell(5).setCellValue(alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : "");
                }
            }

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }
    }
}

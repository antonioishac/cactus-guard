package br.com.cactus.guard.report.service;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.report.entity.ReportJob;
import br.com.cactus.guard.report.repository.ReportJobRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ReportJobRepository jobRepository;
    private final AlertRepository alertRepository;

    @Async
    public void generateAsync(ReportJob job) {
        try {
            job.setStatus("PROCESSING");
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            Path outputDir = Path.of("reports");
            Files.createDirectories(outputDir);
            String fileName = job.getReportCode() + "_" + job.getId() + ".pdf";
            Path filePath = outputDir.resolve(fileName);

            generatePdf(job, filePath);

            job.setStatus("COMPLETED");
            job.setFilePath(filePath.toString());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            log.info("Report generated: {}", filePath);
        } catch (Exception e) {
            log.error("Report generation failed", e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private void generatePdf(ReportJob job, Path filePath) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
        document.open();

        document.add(new Paragraph("CactusGuard - Relatório"));
        document.add(new Paragraph("Código: " + job.getReportCode()));
        document.add(new Paragraph("Gerado em: " + Instant.now()));
        document.add(new Paragraph(" "));

        if ("ALERTS_SUMMARY".equals(job.getReportCode())) {
            long open = alertRepository.countByStatus("OPEN");
            long inAnalysis = alertRepository.countByStatus("IN_ANALYSIS");
            long closed = alertRepository.countByStatus("CLOSED");
            document.add(new Paragraph("Alertas Abertos: " + open));
            document.add(new Paragraph("Em Análise: " + inAnalysis));
            document.add(new Paragraph("Fechados: " + closed));
        }

        document.close();
    }
}

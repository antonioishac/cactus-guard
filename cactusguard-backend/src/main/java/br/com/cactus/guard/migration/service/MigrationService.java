package br.com.cactus.guard.migration.service;

import br.com.cactus.guard.migration.entity.MigrationBatch;
import br.com.cactus.guard.migration.entity.MigrationRecord;
import br.com.cactus.guard.migration.repository.MigrationBatchRepository;
import br.com.cactus.guard.migration.repository.MigrationRecordRepository;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private final MigrationBatchRepository batchRepository;
    private final MigrationRecordRepository recordRepository;
    private final PartyRepository partyRepository;

    @Transactional
    public MigrationBatch createBatch(String sourceSystem) {
        MigrationBatch batch = MigrationBatch.builder()
                .sourceSystem(sourceSystem)
                .status("PENDING")
                .build();
        return batchRepository.save(batch);
    }

    @Transactional
    public void addRecord(UUID batchId, String externalId, String document, Map<String, Object> rawData) {
        MigrationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        MigrationRecord record = MigrationRecord.builder()
                .batch(batch)
                .externalId(externalId)
                .document(document)
                .rawData(rawData)
                .status("PENDING")
                .build();
        recordRepository.save(record);

        batch.setTotalRecords(batch.getTotalRecords() + 1);
        batchRepository.save(batch);
    }

    @Transactional
    public int processBatch(UUID batchId) {
        MigrationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        batch.setStatus("PROCESSING");
        batch.setStartedAt(Instant.now());
        batchRepository.save(batch);

        List<MigrationRecord> pending = recordRepository.findByBatchIdAndStatus(batchId, "PENDING");
        int processed = 0;
        int failed = 0;

        for (MigrationRecord record : pending) {
            try {
                Party party = createOrUpdateParty(record);
                record.setParty(party);
                record.setStatus("PROCESSED");
                record.setProcessedAt(Instant.now());
                processed++;
            } catch (Exception e) {
                record.setStatus("FAILED");
                record.setErrorMessage(e.getMessage());
                failed++;
                log.warn("Migration record failed: {}", e.getMessage());
            }
            recordRepository.save(record);
        }

        batch.setProcessedRecords(processed);
        batch.setFailedRecords(failed);
        batch.setStatus(failed == 0 ? "COMPLETED" : "PARTIAL");
        batch.setCompletedAt(Instant.now());
        batchRepository.save(batch);

        log.info("Batch {} processed: {} success, {} failed", batchId, processed, failed);
        return processed;
    }

    private Party createOrUpdateParty(MigrationRecord record) {
        String hash = hashDocument(record.getDocument());
        return partyRepository.findByDocumentHash(hash)
                .orElseGet(() -> {
                    Map<String, Object> data = record.getRawData();
                    Party party = Party.builder()
                            .type(data != null && data.containsKey("type") ? data.get("type").toString() : "PF")
                            .document(record.getDocument())
                            .documentHash(hash)
                            .name(data != null && data.containsKey("name") ? data.get("name").toString() : "Migrado")
                            .email(data != null && data.containsKey("email") ? data.get("email").toString() : null)
                            .status("ACTIVE")
                            .riskLevel("PENDING")
                            .build();
                    return partyRepository.save(party);
                });
    }

    private String hashDocument(String document) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash calculation failed", e);
        }
    }
}

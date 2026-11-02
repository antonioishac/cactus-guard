package br.com.cactus.guard.kyc.service;

import br.com.cactus.guard.kyc.dto.KycChecklistResponse;
import br.com.cactus.guard.kyc.entity.KycChecklistItem;
import br.com.cactus.guard.kyc.repository.KycChecklistRepository;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycChecklistService {

    private final KycChecklistRepository checklistRepository;
    private final KycProcessRepository processRepository;

    @Transactional
    public KycChecklistResponse addItem(UUID processId, String itemCode, String description, boolean required) {
        var process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("KYC process not found"));

        KycChecklistItem item = KycChecklistItem.builder()
                .process(process)
                .itemCode(itemCode)
                .itemDescription(description)
                .required(required)
                .completed(false)
                .build();

        item = checklistRepository.save(item);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<KycChecklistResponse> findByProcess(UUID processId) {
        return checklistRepository.findByProcessId(processId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public KycChecklistResponse complete(UUID itemId) {
        KycChecklistItem item = checklistRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist item not found"));
        item.setCompleted(true);
        item.setCompletedAt(Instant.now());
        item = checklistRepository.save(item);
        return toResponse(item);
    }

    private KycChecklistResponse toResponse(KycChecklistItem item) {
        return KycChecklistResponse.builder()
                .id(item.getId())
                .processId(item.getProcess().getId())
                .itemCode(item.getItemCode())
                .itemDescription(item.getItemDescription())
                .required(item.isRequired())
                .completed(item.isCompleted())
                .completedAt(item.getCompletedAt())
                .build();
    }
}

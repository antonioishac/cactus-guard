package br.com.cactus.guard.event.repository;

import br.com.cactus.guard.event.entity.DeadLetterEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    Page<DeadLetterEvent> findByStatus(String status, Pageable pageable);

    boolean existsBySourceTopicAndSourcePartitionAndSourceOffset(String topic, int partition, long offset);

    Optional<DeadLetterEvent> findBySourceTopicAndSourcePartitionAndSourceOffset(String topic, int partition, long offset);
}

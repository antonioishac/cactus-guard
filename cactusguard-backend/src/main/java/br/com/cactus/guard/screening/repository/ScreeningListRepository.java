package br.com.cactus.guard.screening.repository;

import br.com.cactus.guard.screening.entity.ScreeningList;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningListRepository extends JpaRepository<ScreeningList, UUID> {
    Optional<ScreeningList> findByCode(String code);
}

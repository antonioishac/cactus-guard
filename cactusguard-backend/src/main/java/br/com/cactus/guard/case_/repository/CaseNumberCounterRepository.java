package br.com.cactus.guard.case_.repository;

import br.com.cactus.guard.case_.entity.CaseNumberCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CaseNumberCounterRepository extends JpaRepository<CaseNumberCounter, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CaseNumberCounter c WHERE c.year = :year")
    Optional<CaseNumberCounter> findForUpdate(@Param("year") int year);
}

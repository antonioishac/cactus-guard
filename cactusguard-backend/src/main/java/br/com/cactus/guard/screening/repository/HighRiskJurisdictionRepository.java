package br.com.cactus.guard.screening.repository;

import br.com.cactus.guard.screening.entity.HighRiskJurisdiction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HighRiskJurisdictionRepository extends JpaRepository<HighRiskJurisdiction, UUID> {

    /** Entradas vigentes para um pais em uma data. */
    @Query("""
           select j from HighRiskJurisdiction j
            where j.countryCode = :countryCode
              and j.validFrom <= :date
              and (j.validTo is null or j.validTo >= :date)
           """)
    List<HighRiskJurisdiction> findActiveByCountry(@Param("countryCode") String countryCode,
                                                   @Param("date") LocalDate date);

    List<HighRiskJurisdiction> findByValidToIsNull();
}

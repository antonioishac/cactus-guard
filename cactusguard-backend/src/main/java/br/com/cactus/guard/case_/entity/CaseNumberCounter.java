package br.com.cactus.guard.case_.entity;

import jakarta.persistence.*;
import lombok.*;

/** Contador anual da numeracao legivel dos cases (V31). */
@Entity
@Table(name = "case_number_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CaseNumberCounter {

    @Id
    @Column(name = "counter_year")
    private int year;

    @Column(name = "last_value", nullable = false)
    private long lastValue;
}

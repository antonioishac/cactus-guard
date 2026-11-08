package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.case_.entity.CaseNumberCounter;
import br.com.cactus.guard.case_.repository.CaseNumberCounterRepository;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Year;

/**
 * Numeracao legivel e sequencial por ano (CG-AAAA-NNNNNN). Cada reserva roda em transacao propria
 * e curta (o lock da linha do contador e liberado logo; nao serializa a criacao dos cases). A corrida
 * na criacao do contador do ano e resolvida com nova tentativa em outra transacao.
 */
@Service
public class CaseNumberService {

    private final CaseNumberCounterRepository counters;
    private final TransactionTemplate tx;

    public CaseNumberService(CaseNumberCounterRepository counters, PlatformTransactionManager tm) {
        this.counters = counters;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String next() {
        int year = Year.now(BusinessCalendar.BRAZIL).getValue();
        DataIntegrityViolationException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return tx.execute(status -> {
                    CaseNumberCounter c = counters.findForUpdate(year).orElse(null);
                    if (c == null) {
                        c = counters.saveAndFlush(new CaseNumberCounter(year, 0));
                    }
                    c.setLastValue(c.getLastValue() + 1);
                    counters.save(c);
                    return format(year, c.getLastValue());
                });
            } catch (DataIntegrityViolationException race) {
                last = race;
            }
        }
        throw last;
    }

    public static String format(int year, long seq) {
        return "CG-%d-%06d".formatted(year, seq);
    }
}

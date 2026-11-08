package br.com.cactus.guard.screening.dto;

import br.com.cactus.guard.screening.entity.ScreeningList;

import java.time.Instant;

/** Catalogo de listas de screening (spec 011). {@code code} e o valor aceito em {@code lists}. */
public record ScreeningListResponse(String code, String name, String type, String source,
                                    boolean requiresAssetFreeze, boolean active, Instant lastUpdated) {

    public static ScreeningListResponse from(ScreeningList l) {
        return new ScreeningListResponse(l.getCode(), l.getName(), l.getType(), l.getSource(),
                l.isRequiresAssetFreeze(), l.isActive(), l.getLastUpdated());
    }
}

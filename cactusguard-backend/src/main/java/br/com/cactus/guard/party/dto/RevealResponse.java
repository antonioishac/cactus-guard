package br.com.cactus.guard.party.dto;

import java.util.Map;

/** So os campos pedidos. {@code field}/{@code value} = primeiro campo; {@code values} = todos. */
public record RevealResponse(String field, String value, Map<String, String> values) {
}

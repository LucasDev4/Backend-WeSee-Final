package com.atlas.backend_atlas.service.geo;

import com.fasterxml.jackson.databind.JsonNode;

/** Extrai campos do objeto "address" do Nominatim e monta o endereço curto. */
final class AddressFormatter {

    private AddressFormatter() {}

    /** Primeiro campo não vazio entre as chaves; null se nenhum existir. */
    static String first(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (v.isTextual() && !v.asText().isBlank()) return v.asText().trim();
        }
        return null;
    }

    static String road(JsonNode address) {
        return first(address, "road", "pedestrian", "footway", "path", "cycleway", "street");
    }

    static String suburb(JsonNode address) {
        return first(address, "suburb", "neighbourhood", "quarter", "city_district");
    }

    static String city(JsonNode address) {
        return first(address, "city", "town", "village", "municipality");
    }

    /** "Rua X, 123 - Bairro"; sem rua, usa o nome do local; sem nada, as 2 primeiras partes do display_name. */
    static String shortAddress(String name, String road, String houseNumber, String suburb, String displayName) {
        StringBuilder sb = new StringBuilder();
        if (road != null) {
            sb.append(road);
            if (houseNumber != null) sb.append(", ").append(houseNumber);
        } else if (name != null && !name.isBlank()) {
            sb.append(name);
        }
        if (suburb != null && sb.length() > 0) sb.append(" - ").append(suburb);
        if (sb.length() == 0 && displayName != null && !displayName.isBlank()) {
            String[] parts = displayName.split(",");
            sb.append(parts[0].trim());
            if (parts.length > 1) sb.append(", ").append(parts[1].trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}

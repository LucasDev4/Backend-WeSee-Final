package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.exception.GeoException;

/** Validação e utilitários de coordenadas, num só lugar. */
public final class GeoValidation {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoValidation() {}

    public static boolean isValid(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
    }

    /** @throws GeoException 400 se a coordenada for inválida; [rotulo] diz qual ("origem", "destino"...). */
    public static void exigirValida(double lat, double lon, String rotulo) {
        if (!isValid(lat, lon)) {
            throw GeoException.invalido("Coordenadas inválidas em " + rotulo
                    + ". Latitude deve estar entre -90 e 90 e longitude entre -180 e 180.");
        }
    }

    /** Distância em metros entre dois pontos (fórmula de haversine). */
    public static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}

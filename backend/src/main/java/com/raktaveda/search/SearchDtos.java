package com.raktaveda.search;

public final class SearchDtos {

    private SearchDtos() {
    }

    /** One hospital or blood bank, ready to draw as a pin on a map. distanceKm is rounded to 0.1 km. */
    public record NearbyInstitution(String id, String name, String type, String address, String publicPhone,
                                    double latitude, double longitude, double distanceKm) {
    }
}

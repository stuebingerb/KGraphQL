package com.apurebase.kgraphql.schema;

// LatLng class with two constructors, which is not supported
public class InvalidLatLng {
    public double lat;
    public double lng;

    public InvalidLatLng() {}

    public InvalidLatLng(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }
}

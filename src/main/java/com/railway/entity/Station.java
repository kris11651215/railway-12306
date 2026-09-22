package com.railway.entity;

import java.util.Objects;

public class Station extends RailwayEntity {

    private String stationCode;
    private String stationName;
    private String city;
    private String bureau;
    private String affiliatedDepot;
    private String stationClass;
    private boolean hub;

    public Station() {
    }

    public Station(Long id, String stationCode, String stationName, String city,
                   String bureau, String affiliatedDepot, String stationClass, boolean hub) {
        super(id);
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.city = city;
        this.bureau = bureau;
        this.affiliatedDepot = affiliatedDepot;
        this.stationClass = stationClass;
        this.hub = hub;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getBureau() {
        return bureau;
    }

    public void setBureau(String bureau) {
        this.bureau = bureau;
    }

    public String getAffiliatedDepot() {
        return affiliatedDepot;
    }

    public void setAffiliatedDepot(String affiliatedDepot) {
        this.affiliatedDepot = affiliatedDepot;
    }

    public String getStationClass() {
        return stationClass;
    }

    public void setStationClass(String stationClass) {
        this.stationClass = stationClass;
    }

    public boolean isHub() {
        return hub;
    }

    public void setHub(boolean hub) {
        this.hub = hub;
    }

    @Override
    public RailwaySystem getSystemCategory() {
        return RailwaySystem.CAR_SERVICE;
    }

    @Override
    public String toString() {
        return String.format("Station{code=%s, name=%s, city=%s, bureau=%s, depot=%s, class=%s, hub=%s}",
                stationCode, stationName, city, bureau, affiliatedDepot, stationClass, hub);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Station)) {
            return false;
        }
        Station station = (Station) o;
        return Objects.equals(stationCode, station.stationCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stationCode);
    }
}

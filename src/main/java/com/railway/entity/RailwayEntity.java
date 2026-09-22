package com.railway.entity;

public abstract class RailwayEntity {

    private Long id;

    protected RailwayEntity() {
    }

    protected RailwayEntity(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public abstract RailwaySystem getSystemCategory();
}

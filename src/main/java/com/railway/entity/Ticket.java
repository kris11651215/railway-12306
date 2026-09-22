package com.railway.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public class Ticket extends RailwayEntity {

    private String ticketNo;
    private String trainNo;
    private String fromStation;
    private String toStation;
    private SeatType seatType;
    private LocalDate travelDate;
    private LocalTime departureTime;
    private BigDecimal price;
    private String carriageNo;
    private String seatNo;
    private String passengerName;
    private TicketStatus status;

    public Ticket() {
    }

    public Ticket(Long id, String ticketNo, String trainNo, String fromStation, String toStation,
                  SeatType seatType, LocalDate travelDate, LocalTime departureTime, BigDecimal price,
                  String carriageNo, String seatNo, String passengerName, TicketStatus status) {
        super(id);
        this.ticketNo = ticketNo;
        this.trainNo = trainNo;
        this.fromStation = fromStation;
        this.toStation = toStation;
        this.seatType = seatType;
        this.travelDate = travelDate;
        this.departureTime = departureTime;
        this.price = price;
        this.carriageNo = carriageNo;
        this.seatNo = seatNo;
        this.passengerName = passengerName;
        this.status = status;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public String getFromStation() {
        return fromStation;
    }

    public void setFromStation(String fromStation) {
        this.fromStation = fromStation;
    }

    public String getToStation() {
        return toStation;
    }

    public void setToStation(String toStation) {
        this.toStation = toStation;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(LocalDate travelDate) {
        this.travelDate = travelDate;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCarriageNo() {
        return carriageNo;
    }

    public void setCarriageNo(String carriageNo) {
        this.carriageNo = carriageNo;
    }

    public String getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(String seatNo) {
        this.seatNo = seatNo;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    @Override
    public RailwaySystem getSystemCategory() {
        return RailwaySystem.CAR_SERVICE;
    }

    @Override
    public String toString() {
        return String.format("Ticket{no=%s, train=%s, %s→%s, date=%s, seat=%s, status=%s}",
                ticketNo, trainNo, fromStation, toStation, travelDate,
                seatType == null ? null : seatType.getChineseName(),
                status == null ? null : status.getChineseName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ticket)) {
            return false;
        }
        Ticket ticket = (Ticket) o;
        return Objects.equals(ticketNo, ticket.ticketNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketNo);
    }
}

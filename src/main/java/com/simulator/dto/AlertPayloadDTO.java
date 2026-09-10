package com.simulator.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"vehicleId", "alertType", "value"})
public class AlertPayloadDTO {

    private String vehicleId;
    private String alertType;
    private String value;

    public AlertPayloadDTO() {}

    public AlertPayloadDTO(String vehicleId, String alertType, String value) {
        this.vehicleId = vehicleId;
        this.alertType = alertType;
        this.value = value;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
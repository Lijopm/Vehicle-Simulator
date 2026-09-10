package com.simulator.dto;

public class EventInjectionDTO {
    private String vehicleId;
    private String eventType; // OVERSPEEDING, HARSH_BRAKING, RAPID_ACCELERATION, HIGH_ENGINE_TEMPERATURE, EXCESSIVE_IDLING, LOW_FUEL, FAULT_CODE
    private String value;

    public EventInjectionDTO() {}

    public EventInjectionDTO(String vehicleId, String eventType, String value) {
        this.vehicleId = vehicleId;
        this.eventType = eventType;
        this.value = value;
    }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}

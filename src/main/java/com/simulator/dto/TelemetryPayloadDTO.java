package com.simulator.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class TelemetryPayloadDTO {

    private String vehicleId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private Double speed;

    private Double engineTemperature;

    private Double latitude;

    private Double longitude;

    private Double fuelLevel;

    private Integer rpm;

    private Double odometerReading;

    private String engineStatus;

    private String faultCodes;

    public TelemetryPayloadDTO() {}

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public Double getEngineTemperature() { return engineTemperature; }
    public void setEngineTemperature(Double engineTemperature) { this.engineTemperature = engineTemperature; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(Double fuelLevel) { this.fuelLevel = fuelLevel; }

    public Integer getRpm() { return rpm; }
    public void setRpm(Integer rpm) { this.rpm = rpm; }

    public Double getOdometerReading() { return odometerReading; }
    public void setOdometerReading(Double odometerReading) { this.odometerReading = odometerReading; }

    public String getEngineStatus() { return engineStatus; }
    public void setEngineStatus(String engineStatus) { this.engineStatus = engineStatus; }

    public String getFaultCodes() { return faultCodes; }
    public void setFaultCodes(String faultCodes) { this.faultCodes = faultCodes; }
}

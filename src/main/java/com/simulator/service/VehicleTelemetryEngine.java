package com.simulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.simulator.dto.AlertPayloadDTO;
import com.simulator.dto.TelemetryPayloadDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class VehicleTelemetryEngine {

    private static final Logger log = LoggerFactory.getLogger(VehicleTelemetryEngine.class);

    private final MqttPublisherService mqttPublisherService;
    private final ObjectMapper objectMapper;

    @Value("${simulator.enabled:true}")
    private boolean enabledByDefault;

    @Value("${simulator.interval-ms:1000}")
    private long intervalMs;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> simulationTask;
    private volatile boolean isRunning = false;

    private final Map<String, VehicleSimulationState> vehicles = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlertTimestamps = new ConcurrentHashMap<>();

    public VehicleTelemetryEngine(MqttPublisherService mqttPublisherService) {
        this.mqttPublisherService = mqttPublisherService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Initialize 20 vehicles: ENGIN_0001 through ENGIN_0020
        Random random = new Random(42);
        for (int i = 1; i <= 20; i++) {
            String vehicleId = String.format("ENGIN_%04d", i);

            // Spread coordinates across the greater metropolitan & highway corridors (around Pune/Mumbai base)
            double baseLat = 18.5204 + (random.nextDouble() - 0.5) * 0.12;
            double baseLon = 73.8567 + (random.nextDouble() - 0.5) * 0.12;

            // Speed range between 50 and 120 km/h
            double speed = 50.0 + random.nextDouble() * 70.0;

            // Engine temperature range between 50°C and 130°C
            double temp = 50.0 + random.nextDouble() * 80.0;

            // Fuel level between 25% and 95%
            double fuel = 25.0 + random.nextDouble() * 70.0;

            int rpm = (int) (speed * 32.0 + 900);
            double odo = 8000.0 + (i * 1850.0);

            vehicles.put(vehicleId, new VehicleSimulationState(vehicleId, baseLat, baseLon, speed, temp, fuel, rpm, odo, i));
        }
    }

    @PostConstruct
    public void init() {
        if (enabledByDefault) {
            start();
        }
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        simulationTask = scheduler.scheduleAtFixedRate(this::tick, 1000, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Vehicle Telemetry Engine started. Streaming telemetry for {} vehicles (ENGIN_0001 to ENGIN_0020)...", vehicles.size());
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        if (simulationTask != null) {
            simulationTask.cancel(false);
        }
        log.info("Vehicle Telemetry Engine paused.");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public Set<String> getSimulatedVehicleIds() {
        return vehicles.keySet();
    }

    public boolean injectEvent(String vehicleId, String eventType) {
        return injectEvent(vehicleId, eventType, null);
    }

    public boolean injectEvent(String vehicleId, String eventType, String customValue) {
        VehicleSimulationState state = vehicles.get(vehicleId);
        if (state == null) {
            return false;
        }

        log.info("Simulator injecting event '{}' into vehicle {}", eventType, vehicleId);
        String alertType;
        String alertVal;

        switch (eventType.toUpperCase()) {
            case "OVERSPEEDING":
            case "OVERSPEED":
                alertType = "OVERSPEEDING";
                alertVal = (customValue != null) ? customValue : "118.0";
                break;
            case "HARSH_BRAKING":
                alertType = "HARSH_BRAKING";
                alertVal = (customValue != null) ? customValue : "35.0";
                break;
            case "RAPID_ACCELERATION":
                alertType = "RAPID_ACCELERATION";
                alertVal = (customValue != null) ? customValue : "32.0";
                break;
            case "HIGH_ENGINE_TEMPERATURE":
            case "OVERHEATING":
                alertType = "OVERHEATING";
                alertVal = (customValue != null) ? customValue : "124.5";
                break;
            case "EXCESSIVE_IDLING":
                alertType = "EXCESSIVE_IDLING";
                alertVal = (customValue != null) ? customValue : "65";
                break;
            case "LOW_FUEL":
                alertType = "LOW_FUEL";
                alertVal = (customValue != null) ? customValue : "15";
                break;
            default:
                alertType = eventType;
                alertVal = (customValue != null) ? customValue : "0";
                break;
        }

        emitAlert(vehicleId, alertType, alertVal);
        return true;
    }

    private void tick() {
        try {
            for (VehicleSimulationState state : vehicles.values()) {
                state.step();
                emitVehicleTelemetry(state);

                // Push alerts to separate topic 'alerts/{vehicleId}' when threshold condition met
                // 1. Low Fuel Alert (e.g. value 15)
                if (state.fuelLevel <= 15.0) {
                    checkAndEmitAlert(state.vehicleId, "LOW_FUEL", "15", 8000);
                }

                // 2. Overheating Alert (value more than 100°C)
                if (state.engineTemperature > 100.0) {
                    String tempVal = String.format(Locale.US, "%.1f", state.engineTemperature);
                    checkAndEmitAlert(state.vehicleId, "OVERHEATING", tempVal, 8000);
                }

                // 3. Overspeeding Alert (value > 100 km/h)
                if (state.speed > 100.0) {
                    String speedVal = String.format(Locale.US, "%.1f", state.speed);
                    checkAndEmitAlert(state.vehicleId, "OVERSPEEDING", speedVal, 8000);
                }
            }
        } catch (Exception e) {
            log.error("Error during telemetry generation tick", e);
        }
    }

    public void emitAlert(String vehicleId, String alertType, String value) {
        try {
            AlertPayloadDTO alertDto = new AlertPayloadDTO(vehicleId, alertType, String.valueOf(value));
            String json = objectMapper.writeValueAsString(alertDto);

            // Separate topic alerts with vehicle id
            String alertTopic = "alerts/" + vehicleId;
            String vehicleAlertTopic = "vehicle/" + vehicleId + "/alerts";

            mqttPublisherService.publish(alertTopic, json);
            mqttPublisherService.publish(vehicleAlertTopic, json);

            log.info("Emitted alert on topic '{}': {}", alertTopic, json);
        } catch (Exception e) {
            log.error("Failed to emit alert on topic alerts/{} for vehicle {}", vehicleId, vehicleId, e);
        }
    }

    private void checkAndEmitAlert(String vehicleId, String alertType, String value, long debounceMs) {
        String key = vehicleId + ":" + alertType;
        long now = System.currentTimeMillis();
        Long last = lastAlertTimestamps.get(key);
        if (last == null || (now - last) >= debounceMs) {
            lastAlertTimestamps.put(key, now);
            emitAlert(vehicleId, alertType, value);
        }
    }

    private void emitVehicleTelemetry(VehicleSimulationState state) {
        try {
            TelemetryPayloadDTO payload = new TelemetryPayloadDTO();
            payload.setVehicleId(state.vehicleId);
            payload.setTimestamp(LocalDateTime.now());
            payload.setSpeed(Math.round(state.speed * 10.0) / 10.0);
            payload.setEngineTemperature(Math.round(state.engineTemperature * 10.0) / 10.0);
            payload.setLatitude(Math.round(state.latitude * 10000.0) / 10000.0);
            payload.setLongitude(Math.round(state.longitude * 10000.0) / 10000.0);
            payload.setFuelLevel(Math.round(state.fuelLevel * 10.0) / 10.0);
            payload.setRpm(state.rpm);
            payload.setOdometerReading(Math.round(state.odometerReading * 10.0) / 10.0);
            payload.setEngineStatus(state.speed > 0 ? "RUNNING" : "IDLE");
            payload.setFaultCodes(state.faultCodes);

            String json = objectMapper.writeValueAsString(payload);
            String topic = "vehicle/" + state.vehicleId + "/telemetry";
            mqttPublisherService.publish(topic, json);

            state.faultCodes = null;
        } catch (Exception e) {
            log.error("Failed to emit telemetry for vehicle {}", state.vehicleId, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    public static class VehicleSimulationState {
        public String vehicleId;
        public double latitude;
        public double longitude;
        public double speed;
        public double engineTemperature;
        public double fuelLevel;
        public int rpm;
        public double odometerReading;
        public String faultCodes;

        private double heading;
        private double speedTrend = 1.0; // 1.0 for accelerating, -1.0 for decelerating
        private final Random random = new Random();

        public VehicleSimulationState(String vehicleId, double latitude, double longitude, double speed,
                                      double engineTemperature, double fuelLevel, int rpm, double odometerReading, int seed) {
            this.vehicleId = vehicleId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.speed = speed;
            this.engineTemperature = engineTemperature;
            this.fuelLevel = fuelLevel;
            this.rpm = rpm;
            this.odometerReading = odometerReading;
            this.heading = (seed * 18.0) % 360.0;
        }

        public void step() {
            // 1. Dynamic Speed Variation (50 km/h to 120 km/h)
            // Gently oscillates and drifts within 50 - 120 km/h range
            if (random.nextDouble() < 0.08) {
                speedTrend = -speedTrend; // Reverse trend
            }

            // Normal speed delta
            double speedDelta = (speedTrend * (0.8 + random.nextDouble() * 2.2));
            speed += speedDelta;

            // Keep within bounds [50.0, 120.0]
            if (speed > 120.0) {
                speed = 120.0;
                speedTrend = -1.0;
            } else if (speed < 50.0) {
                speed = 50.0;
                speedTrend = 1.0;
            }

            // 2. Engine Temperature Variation (50°C to 130°C)
            // Higher speeds drive temperature up; lower speeds allow cooling
            double targetTemp;
            if (speed > 105.0) {
                // High speed heats engine above 110°C (up to 128°C)
                targetTemp = 112.0 + (speed - 105.0) * 0.9 + (random.nextDouble() * 5.0);
            } else if (speed > 80.0) {
                targetTemp = 88.0 + (speed - 80.0) * 0.7;
            } else {
                targetTemp = 58.0 + (speed - 50.0) * 0.8;
            }

            // Smooth temperature transition towards targetTemp
            engineTemperature += (targetTemp - engineTemperature) * 0.04 + (random.nextDouble() - 0.5) * 0.4;
            // Clamp within [50.0, 130.0]
            engineTemperature = Math.max(50.0, Math.min(130.0, engineTemperature));

            // 3. RPM Calculation (correlated with speed)
            rpm = (int) (speed * 34.0 + 850 + (random.nextInt(100) - 50));

            // 4. Distance & Odometer Update
            double distanceKm = (speed / 3600.0);
            odometerReading += distanceKm;

            // 5. Fuel Level Depletion & Automatic Refuel Cycle
            // Fuel burns proportional to speed and distance
            fuelLevel -= (distanceKm * 0.08);
            if (fuelLevel <= 3.0) {
                // Refuel event back to 95% so simulation can run indefinitely
                fuelLevel = 95.0 + random.nextDouble() * 5.0;
            }

            // 6. GPS Route Movement
            heading += (random.nextDouble() - 0.5) * 6.0;
            double rad = Math.toRadians(heading);
            double deltaLat = (distanceKm / 111.0) * Math.cos(rad);
            double deltaLon = (distanceKm / (111.0 * Math.cos(Math.toRadians(latitude)))) * Math.sin(rad);

            latitude += deltaLat;
            longitude += deltaLon;
        }
    }
}

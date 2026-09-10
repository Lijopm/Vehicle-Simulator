package com.simulator.controller;

import com.simulator.dto.EventInjectionDTO;
import com.simulator.service.EmbeddedMqttServer;
import com.simulator.service.MqttPublisherService;
import com.simulator.service.VehicleTelemetryEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
@CrossOrigin(origins = "*")
public class SimulatorApiController {

    private final VehicleTelemetryEngine telemetryEngine;
    private final MqttPublisherService mqttPublisherService;
    private final EmbeddedMqttServer embeddedMqttServer;

    public SimulatorApiController(VehicleTelemetryEngine telemetryEngine,
                                  MqttPublisherService mqttPublisherService,
                                  EmbeddedMqttServer embeddedMqttServer) {
        this.telemetryEngine = telemetryEngine;
        this.mqttPublisherService = mqttPublisherService;
        this.embeddedMqttServer = embeddedMqttServer;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "running", telemetryEngine.isRunning(),
                "mqttServerRunning", embeddedMqttServer.isRunning(),
                "mqttConnected", mqttPublisherService.isConnected(),
                "simulatedVehicles", telemetryEngine.getSimulatedVehicleIds()
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<?> start() {
        telemetryEngine.start();
        return ResponseEntity.ok(Map.of("message", "Telemetry streaming started", "running", true));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        telemetryEngine.stop();
        return ResponseEntity.ok(Map.of("message", "Telemetry streaming stopped", "running", false));
    }

    @PostMapping("/inject")
    public ResponseEntity<?> injectEvent(@RequestBody EventInjectionDTO request) {
        String vId = (request.getVehicleId() != null) ? request.getVehicleId() : "ENGIN_0001";
        if (request.getEventType() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventType is required"));
        }

        boolean success = telemetryEngine.injectEvent(vId, request.getEventType(), request.getValue());
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "message", "Anomaly event injected and published to MQTT alert and telemetry topics",
                    "vehicleId", vId,
                    "eventType", request.getEventType(),
                    "value", request.getValue() != null ? request.getValue() : "default"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown vehicleId or eventType"));
        }
    }
}

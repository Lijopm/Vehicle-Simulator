package com.simulator;

import com.simulator.service.VehicleTelemetryEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VehicleSimulatorApplicationTests {

    @Autowired
    private VehicleTelemetryEngine telemetryEngine;

    @Test
    void contextLoads() {
        assertNotNull(telemetryEngine);
        assertEquals(20, telemetryEngine.getSimulatedVehicleIds().size());
        assertTrue(telemetryEngine.getSimulatedVehicleIds().contains("ENGIN_0001"));
        assertTrue(telemetryEngine.getSimulatedVehicleIds().contains("ENGIN_0020"));
    }

    @Test
    void testAnomalyInjection() {
        boolean injected = telemetryEngine.injectEvent("ENGIN_0001", "OVERSPEEDING");
        assertTrue(injected);
    }
}

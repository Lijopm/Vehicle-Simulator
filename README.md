# Vehicle Simulator Service - Standalone Telemetry Publisher

An independent, standalone vehicle telemetry simulator built with **Java 21** and **Spring Boot 3.3**. It generates realistic vehicle movement and sensor readings, streaming periodic JSON payloads over **MQTT** to `vehicle/{vehicleId}/telemetry`.

## Features

- **Multi-Vehicle Simulation**: Simulates vehicles (`VH001`, `VH002`, `VH003`) with GPS coordinate loops, velocity physics, engine temperatures, fuel burn, and RPM gear curves.
- **Image 3 JSON Telemetry Schema**:
  ```json
  {
    "vehicleId": "VH001",
    "timestamp": "2026-09-10T10:30:00",
    "speed": 82.0,
    "engineTemperature": 95.0,
    "latitude": 18.5204,
    "longitude": 73.8567,
    "fuelLevel": 68.0,
    "rpm": 3200,
    "odometerReading": 14250.0,
    "engineStatus": "RUNNING"
  }
  ```
- **MQTT Publisher**: Publishes to `tcp://localhost:1883` on topic `vehicle/{vehicleId}/telemetry`.
- **On-Demand Anomaly Injection API**:
  Exposes REST controls on port `8085`:
  - `POST /api/simulator/inject`:
    ```json
    {
      "vehicleId": "VH001",
      "eventType": "OVERSPEEDING"
    }
    ```
    Supported `eventType` values:
    - `OVERSPEEDING` (jumps speed to 114 km/h)
    - `HARSH_BRAKING` (drops speed abruptly by 36 km/h)
    - `RAPID_ACCELERATION` (jumps speed abruptly by 32 km/h)
    - `HIGH_ENGINE_TEMPERATURE` (heats engine to 109°C)
    - `EXCESSIVE_IDLING` (sets speed = 0, RPM = 950)
    - `LOW_FUEL` (drops fuel to 9%)
    - `FAULT_CODE` (injects P0117 diagnostic code)

## Quick Start

Run the standalone service:
```powershell
.\gradlew.bat bootRun
```
Or run the packaged JAR:
```powershell
java -jar build/libs/vehicle-simulator-service-1.0.0.jar
```
Service runs on: **`http://localhost:8085`**
Streaming directly to MQTT broker at: **`tcp://localhost:1883`**

package com.simulator.service;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

@Service("embeddedMqttServer")
public class EmbeddedMqttServer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedMqttServer.class);

    private Server mqttServer;

    @Value("${mqtt.server.enabled:true}")
    private boolean enabled;

    @Value("${mqtt.server.port:1883}")
    private int port;

    @Value("${mqtt.server.host:0.0.0.0}")
    private String host;

    @PostConstruct
    public void startServer() {
        if (!enabled) {
            log.info("Embedded MQTT server is disabled via configuration.");
            return;
        }

        if (!isPortAvailable(port)) {
            log.info("Port {} is already in use (e.g., Mosquitto or EMQX is running). Simulator will publish to the existing broker.", port);
            return;
        }

        try {
            Properties properties = new Properties();
            properties.put(IConfig.PORT_PROPERTY_NAME, String.valueOf(port));
            properties.put(IConfig.HOST_PROPERTY_NAME, host);
            properties.put(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
            properties.put(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");

            IConfig config = new MemoryConfig(properties);
            mqttServer = new Server();
            mqttServer.startServer(config);
            log.info("===============================================================");
            log.info("  EMBEDDED MQTT SERVER STARTED ON PORT {}", port);
            log.info("  Clients (Backend, etc.) can connect to tcp://localhost:{}", port);
            log.info("===============================================================");
        } catch (Throwable t) {
            log.warn("Could not start embedded MQTT server on port {}: {}. Continuing in client-only mode.", port, t.getMessage());
        }
    }

    public boolean isRunning() {
        return mqttServer != null;
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @PreDestroy
    public void stopServer() {
        if (mqttServer != null) {
            try {
                mqttServer.stopServer();
                log.info("Embedded MQTT server stopped.");
            } catch (Exception e) {
                log.error("Error stopping embedded MQTT server", e);
            }
        }
    }
}

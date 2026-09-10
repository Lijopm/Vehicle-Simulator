package com.simulator.service;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.DependsOn;
import java.util.concurrent.TimeUnit;

@Service
@DependsOn("embeddedMqttServer")
public class MqttPublisherService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisherService.class);

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.broker.clientId:standalone-vehicle-simulator}")
    private String clientId;

    @Value("${mqtt.broker.username:}")
    private String username;

    @Value("${mqtt.broker.password:}")
    private String password;

    private MqttClient mqttClient;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Schedule connection shortly after embedded broker startup
        reconnectScheduler.schedule(this::connect, 600, TimeUnit.MILLISECONDS);
    }

    public synchronized void connect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) return;

            log.info("Simulator connecting to MQTT Broker at {}", brokerUrl);
            mqttClient = new MqttClient(brokerUrl, clientId + "-" + System.currentTimeMillis(), new MemoryPersistence());
            mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(20);

            if (username != null && !username.trim().isEmpty()) {
                options.setUserName(username);
            }
            if (password != null && !password.trim().isEmpty()) {
                options.setPassword(password.toCharArray());
            }

            mqttClient.connect(options);
            log.info("Simulator successfully connected to MQTT Broker at {}", brokerUrl);
        } catch (MqttException e) {
            log.warn("Simulator could not connect to MQTT broker at {}: {}. Will retry in 4 seconds...", brokerUrl, e.getMessage());
            reconnectScheduler.schedule(this::connect, 4, TimeUnit.SECONDS);
        }
    }

    public boolean publish(String topic, String payload) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                mqttClient.publish(topic, message);
                return true;
            } else {
                log.warn("Cannot publish: MQTT Client is not connected to {}", brokerUrl);
                return false;
            }
        } catch (MqttException e) {
            log.error("Failed to publish to MQTT topic {}: {}", topic, e.getMessage());
            return false;
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("Simulator MQTT connection established (reconnect={}) to {}", reconnect, serverURI);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Simulator lost connection to MQTT Broker: {}", cause != null ? cause.getMessage() : "Unknown");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // Publisher service primarily sends
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void destroy() {
        reconnectScheduler.shutdownNow();
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
            } catch (MqttException e) {
                log.error("Error closing simulator MQTT client", e);
            }
        }
    }
}

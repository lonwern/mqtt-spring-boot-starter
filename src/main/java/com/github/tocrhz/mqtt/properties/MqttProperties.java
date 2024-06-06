package com.github.tocrhz.mqtt.properties;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * MQTT properties.
 *
 * @author tocrhz
 * @see MqttConnectOptions
 */
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties extends ConnectionProperties {
    /**
     * 是否启用
     */
    private Boolean enabled = false;

    /**
     * 多个客户端配置, key:clientId, value:配置
     */
    private Map<String, ConnectionProperties> clients = new LinkedHashMap<>();

    /**
     * 是否启用
     *
     * @return Boolean
     */
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 多个客户端配置, key:clientId, value:配置
     *
     * @return Map
     */
    public Map<String, ConnectionProperties> getClients() {
        return clients;
    }

    public void setClients(Map<String, ConnectionProperties> clients) {
        this.clients = clients;
    }

    /**
     * 遍历所有的客户端配置
     *
     * @param biConsumer String, MqttConnectOptions
     */
    public void forEach(BiConsumer<String, MqttConnectOptions> biConsumer) {
        MqttConnectOptions defaultOptions = toOptions();
        if (defaultOptions != null) {
            biConsumer.accept(getClientId(), defaultOptions);
        }
        if (clients != null && !clients.isEmpty()) {
            // 先遍历一遍处理下clientId冲突的问题
            String[] clientIds = clients.keySet().toArray(new String[0]);
            for (String clientId : clientIds) {
                ConnectionProperties properties = clients.get(clientId);
                String localClientId = properties.getClientId();
                if (StringUtils.hasText(localClientId) && !localClientId.equals(clientId)) {
                    clients.remove(clientId);
                    clients.put(localClientId, properties);
                } else {
                    properties.setClientId(clientId);
                }
            }
            clients.forEach((id, prop) -> {
                MqttConnectOptions options = toOptions(id);
                if (options != null) {
                    biConsumer.accept(id, options);
                }
            });
        }
    }

    /**
     * 转为 MqttConnectOptions
     *
     * @return MqttConnectOptions对象
     */
    private MqttConnectOptions toOptions() {
        if (StringUtils.hasText(getClientId())) {
            return toOptions(getClientId());
        } else {
            return null;
        }
    }

    /**
     * 转为 MqttConnectOptions
     *
     * @param clientId 客户端ID.
     * @return MqttConnectOptions对象
     */
    public MqttConnectOptions toOptions(String clientId) {
        ConnectionProperties properties = clients.get(clientId);
        if (properties == null) {
            if (clientId.equals(getClientId())) {
                properties = this;
            } else {
                return null;
            }
        }
        merge(properties);
        return toOptions(properties);
    }

    private MqttConnectOptions toOptions(ConnectionProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMaxReconnectDelay(properties.getMaxReconnectDelay() * 1000);
        options.setKeepAliveInterval(properties.getKeepAliveInterval());
        options.setConnectionTimeout(properties.getConnectionTimeout()); // fixed day 20221022
        options.setCleanSession(properties.getCleanSession());
        options.setAutomaticReconnect(properties.getAutomaticReconnect());
        options.setExecutorServiceTimeout(properties.getExecutorServiceTimeout());
        options.setServerURIs(properties.getUri());
        if (StringUtils.hasText(properties.getUsername()) && StringUtils.hasText(properties.getPassword())) {
            options.setUserName(properties.getUsername());
            options.setPassword(properties.getPassword().toCharArray());
        }
        if (properties.getWill() != null) {
            WillProperties will = properties.getWill();
            if (StringUtils.hasText(will.getTopic()) && StringUtils.hasText(will.getPayload())) {
                options.setWill(will.getTopic(), will.getPayload().getBytes(StandardCharsets.UTF_8), will.getQos(), will.getRetained());
            }
        }
        return options;
    }

    private void merge(ConnectionProperties target) {
        target.setUri(mergeValue(getUri(), target.getUri(), new String[]{"tcp://127.0.0.1:1883"}));
        target.setUsername(mergeValue(getUsername(), target.getUsername(), null));
        target.setPassword(mergeValue(getPassword(), target.getPassword(), null));
        target.setDefaultPublishQos(mergeValue(getDefaultPublishQos(), target.getDefaultPublishQos(), 0));
        target.setMaxReconnectDelay(mergeValue(getMaxReconnectDelay(), target.getMaxReconnectDelay(), 60));
        target.setKeepAliveInterval(mergeValue(getKeepAliveInterval(), target.getKeepAliveInterval(), 60));
        target.setConnectionTimeout(mergeValue(getConnectionTimeout(), target.getConnectionTimeout(), 30));
        target.setExecutorServiceTimeout(mergeValue(getExecutorServiceTimeout(), target.getExecutorServiceTimeout(), 10));
        target.setCleanSession(mergeValue(getCleanSession(), target.getCleanSession(), true));
        target.setAutomaticReconnect(mergeValue(getAutomaticReconnect(), target.getAutomaticReconnect(), true));
        target.setWill(mergeValue(getWill(), target.getWill(), null));
        target.setEnableSharedSubscription(mergeValue(getEnableSharedSubscription(), target.getEnableSharedSubscription(), true));
        if (target.getWill() != null && getWill() != null) {
            WillProperties will = getWill();
            WillProperties targetWill = target.getWill();
            targetWill.setTopic(mergeValue(will.getTopic(), targetWill.getTopic(), null));
            targetWill.setPayload(mergeValue(will.getPayload(), targetWill.getPayload(), null));
            targetWill.setQos(mergeValue(will.getQos(), targetWill.getQos(), 0));
            targetWill.setRetained(mergeValue(will.getRetained(), targetWill.getRetained(), false));
        }
    }

    private <T> T mergeValue(T parentValue, T targetValue, T defaultValue) {
        if (parentValue == null && targetValue == null) {
            return defaultValue;
        } else if (targetValue == null) {
            return parentValue;
        } else {
            return targetValue;
        }
    }

    public boolean isSharedEnable(String clientId) {
        if (clientId.equals(getClientId())) {
            return getEnableSharedSubscription();
        } else {
            ConnectionProperties properties = clients.get(clientId);
            if (properties == null) {
                return false;
            }
            return properties.getEnableSharedSubscription();
        }
    }

    public int getDefaultPublishQos(String clientId) {
        if (clientId.equals(getClientId())) {
            return getDefaultPublishQos();
        } else {
            ConnectionProperties properties = clients.get(clientId);
            if (properties == null) {
                return 0;
            }
            return properties.getDefaultPublishQos();
        }
    }
}

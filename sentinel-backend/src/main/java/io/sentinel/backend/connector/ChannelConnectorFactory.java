package io.sentinel.backend.connector;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChannelConnectorFactory {

    private final Map<String, ChannelConnector> connectors = new ConcurrentHashMap<>();
    private final MockChannelConnector mock;

    public ChannelConnectorFactory(List<ChannelConnector> allConnectors, MockChannelConnector mock) {
        this.mock = mock;
        allConnectors.forEach(c -> connectors.put(normalize(c.getName()), c));
        connectors.putIfAbsent("MOCK", mock);
    }

    public ChannelConnector get(String channel) {
        if (channel == null || channel.isBlank()) return mock;
        return connectors.getOrDefault(normalize(channel), mock);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}


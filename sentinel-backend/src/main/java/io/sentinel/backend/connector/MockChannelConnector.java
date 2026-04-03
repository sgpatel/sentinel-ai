package io.sentinel.backend.connector;

import io.sentinel.backend.repository.MentionEntity;
import org.springframework.stereotype.Component;

@Component
public class MockChannelConnector implements ChannelConnector {

    @Override
    public String getName() {
        return "MOCK";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String postReply(MentionEntity mention, String replyText) {
        String externalId = "MOCK-POST-" + System.currentTimeMillis();
        System.out.println("[MockChannel] Posted reply for " + mention.id + " -> " + externalId);
        return externalId;
    }
}


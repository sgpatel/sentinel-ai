package io.sentinel.backend.connector;

import io.sentinel.backend.repository.MentionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TwitterChannelConnector implements ChannelConnector {

    @Value("${sentinel.twitter.posting.enabled:false}")
    private boolean postingEnabled;

    @Value("${sentinel.twitter.bearer-token:}")
    private String bearerToken;

    @Override
    public String getName() {
        return "TWITTER";
    }

    @Override
    public boolean isEnabled() {
        return postingEnabled;
    }

    @Override
    public String postReply(MentionEntity mention, String replyText) {
        if (!postingEnabled) {
            throw new IllegalStateException("Twitter posting connector is disabled");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalStateException("Twitter bearer token is missing");
        }
        // Scaffold implementation only: replace with actual Twitter API post call.
        String externalId = "TW-POST-" + System.currentTimeMillis();
        System.out.println("[TwitterChannel] Posted reply for " + mention.id + " -> " + externalId);
        return externalId;
    }
}


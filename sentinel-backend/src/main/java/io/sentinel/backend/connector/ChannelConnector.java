package io.sentinel.backend.connector;

import io.sentinel.backend.repository.MentionEntity;

public interface ChannelConnector {
    String getName();
    boolean isEnabled();
    String postReply(MentionEntity mention, String replyText) throws Exception;
}


package io.sentinel.backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tenant_config")
public class TenantConfigEntity {

    @Id
    @Column(name = "tenant_id")
    public String tenantId;

    @Column(name = "brand_name")
    public String brandName;

    public String handle;
    public String platform = "TWITTER";

    @Column(name = "brand_tone")
    public String brandTone;

    @Column(name = "ticket_system")
    public String ticketSystem = "MOCK";

    @Column(name = "ticket_api_url")
    public String ticketApiUrl;

    @Column(name = "ticket_api_key")
    public String ticketApiKey;

    @Column(name = "auto_reply")
    public boolean autoReply = true;

    @Column(name = "require_approval")
    public boolean requireApproval = true;

    public boolean active = true;

    @Column(name = "created_at")
    public Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}


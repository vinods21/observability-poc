package com.example.tracing.javaservice.trace;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("trace_requests")
public class TraceRequestEntity {

    @Id
    private Long id;

    private String clientMessage;

    private String status;

    private Instant createdAt;

    private Instant updatedAt;

    public TraceRequestEntity() {
    }

    public TraceRequestEntity(Long id, String clientMessage, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.clientMessage = clientMessage;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientMessage() {
        return clientMessage;
    }

    public void setClientMessage(String clientMessage) {
        this.clientMessage = clientMessage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

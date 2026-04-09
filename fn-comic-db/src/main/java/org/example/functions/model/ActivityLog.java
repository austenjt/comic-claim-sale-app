package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityLog {
    private String id;          // UUID, also the partition key
    private String message;
    private String timestamp;   // ISO-8601
    @JsonProperty("isError")
    private boolean isError;
    private int ttl = 604800;   // 7 days — Cosmos uses this for auto-expiry when TTL is enabled on the container
}

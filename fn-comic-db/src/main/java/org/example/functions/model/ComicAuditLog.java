package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComicAuditLog {
    private String id;         // UUID
    private String comicId;
    private String comicTitle;
    private String editedBy;   // admin email, or "system:fulfill", "system:deleteSet"
    private String editedAt;   // ISO-8601 timestamp
    private List<FieldChange> changes;
}

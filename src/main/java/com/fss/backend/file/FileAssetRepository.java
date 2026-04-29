package com.fss.backend.file;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface FileAssetRepository {
    void create(UUID id, String path, String status, UUID createdBy);
    FileAsset findById(UUID id);
    void updateStatus(UUID id, String status, UUID updatedBy);
    List<FileAsset> listInactiveCreatedBefore(OffsetDateTime cutoff);
    void deleteById(UUID id);
}

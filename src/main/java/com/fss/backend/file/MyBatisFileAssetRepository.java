package com.fss.backend.file;

import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisFileAssetRepository implements FileAssetRepository {
    private final FileAssetMapper mapper;

    public MyBatisFileAssetRepository(FileAssetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void create(UUID id, String path, String status, UUID createdBy) {
        mapper.create(id, path, status, createdBy);
    }

    @Override
    public FileAsset findById(UUID id) {
        return mapper.findById(id);
    }

    @Override
    public FileAsset findByPath(String path) {
        return mapper.findByPath(path);
    }

    @Override
    public void updateStatus(UUID id, String status, UUID updatedBy) {
        mapper.updateStatus(id, status, updatedBy);
    }

    @Override
    public int countActiveReferences(UUID id, String path) {
        return mapper.countActiveReferences(id, path);
    }

    @Override
    public List<FileAsset> listInactiveCreatedBefore(OffsetDateTime cutoff) {
        return mapper.listInactiveCreatedBefore(cutoff);
    }

    @Override
    public void deleteById(UUID id) {
        mapper.deleteById(id);
    }
}

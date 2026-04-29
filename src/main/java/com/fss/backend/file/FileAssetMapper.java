package com.fss.backend.file;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface FileAssetMapper {
    void create(@Param("id") UUID id, @Param("path") String path, @Param("status") String status,
                @Param("createdBy") UUID createdBy);
    FileAsset findById(@Param("id") UUID id);
    void updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("updatedBy") UUID updatedBy);
    List<FileAsset> listInactiveCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);
    void deleteById(@Param("id") UUID id);
}

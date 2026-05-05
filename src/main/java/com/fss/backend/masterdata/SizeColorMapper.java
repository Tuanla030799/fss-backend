package com.fss.backend.masterdata;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SizeColorMapper {
    List<SizeOption> listSizes(@Param("status") String status, @Param("keyword") String keyword);
    SizeOption findSizeById(@Param("id") UUID id);
    SizeOption findSizeByValue(@Param("value") String value);
    int countSizeValue(@Param("value") String value, @Param("excludeId") UUID excludeId);
    void insertSize(@Param("id") UUID id, @Param("value") String value, @Param("label") String label,
                    @Param("status") String status, @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);
    void updateSize(@Param("id") UUID id, @Param("value") String value, @Param("label") String label,
                    @Param("status") String status, @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);
    void softDeleteSize(@Param("id") UUID id, @Param("adminId") UUID adminId);

    List<ColorOption> listColors(@Param("status") String status, @Param("keyword") String keyword);
    ColorOption findColorById(@Param("id") UUID id);
    ColorOption findColorByName(@Param("name") String name);
    int countColorName(@Param("name") String name, @Param("excludeId") UUID excludeId);
    void insertColor(@Param("id") UUID id, @Param("name") String name, @Param("colorCode") String colorCode,
                     @Param("status") String status, @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);
    void updateColor(@Param("id") UUID id, @Param("name") String name, @Param("colorCode") String colorCode,
                     @Param("status") String status, @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);
    void softDeleteColor(@Param("id") UUID id, @Param("adminId") UUID adminId);
}

package com.fss.backend.content.collection;

import com.fss.backend.catalog.product.ProductSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ProductCollectionMapper {
    List<ProductCollection> listCollections(@Param("publicOnly") boolean publicOnly,
                                            @Param("status") String status,
                                            @Param("keyword") String keyword,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    ProductCollection findCollectionById(@Param("id") UUID id);

    ProductCollection findCollectionBySlug(@Param("slug") String slug, @Param("publicOnly") boolean publicOnly);

    int countCollectionSlug(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

    void insertCollection(@Param("id") UUID id, @Param("name") String name, @Param("slug") String slug,
                          @Param("description") String description, @Param("fileId") UUID fileId, @Param("status") String status,
                          @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);

    void updateCollection(@Param("id") UUID id, @Param("name") String name, @Param("slug") String slug,
                          @Param("description") String description, @Param("fileId") UUID fileId, @Param("status") String status,
                          @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);

    void softDeleteCollection(@Param("id") UUID id, @Param("adminId") UUID adminId);

    void deleteCollectionProducts(@Param("collectionId") UUID collectionId);

    void insertCollectionProduct(@Param("collectionId") UUID collectionId, @Param("productId") UUID productId,
                                 @Param("sortOrder") Integer sortOrder);

    List<ProductSummary> listCollectionProducts(@Param("collectionId") UUID collectionId,
                                                @Param("publicOnly") boolean publicOnly,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);
}

package com.fss.backend.masterdata;

import com.fss.backend.catalog.brand.Brand;
import com.fss.backend.catalog.brand.BrandMapper;
import com.fss.backend.catalog.category.Category;
import com.fss.backend.catalog.category.CategoryMapper;
import com.fss.backend.content.collection.ProductCollection;
import com.fss.backend.content.collection.ProductCollectionMapper;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MasterDataService {
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ProductCollectionMapper collectionMapper;

    public MasterDataService(CategoryMapper categoryMapper, BrandMapper brandMapper, ProductCollectionMapper collectionMapper) {
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.collectionMapper = collectionMapper;
    }

    public MasterDataResponse publicMasterData() {
        return masterData(true);
    }

    public MasterDataResponse adminMasterData() {
        return masterData(false);
    }

    private MasterDataResponse masterData(boolean publicOnly) {
        String status = publicOnly ? EcommerceSupport.ACTIVE : null;
        return new MasterDataResponse(
                categoryMapper.listCategories(status, null).stream().map(this::categoryOption).toList(),
                brandMapper.listBrands(status, null).stream().map(this::brandOption).toList(),
                collectionMapper.listCollections(publicOnly, status, null, 1000, 0).stream().map(this::collectionOption).toList(),
                List.of(new StaticOption("MALE", "Nam"), new StaticOption("FEMALE", "Nữ"), new StaticOption("UNISEX", "Unisex")),
                List.of(new StaticOption("DRAFT", "Nháp"), new StaticOption("ACTIVE", "Hoạt động"), new StaticOption("INACTIVE", "Ẩn")),
                List.of(new StaticOption("ACTIVE", "Hoạt động"), new StaticOption("INACTIVE", "Ẩn"))
        );
    }

    private MasterDataOption categoryOption(Category category) {
        return new MasterDataOption(category.id(), category.name(), category.slug(), category.status());
    }

    private MasterDataOption brandOption(Brand brand) {
        return new MasterDataOption(brand.id(), brand.name(), brand.slug(), brand.status());
    }

    private MasterDataOption collectionOption(ProductCollection collection) {
        return new MasterDataOption(collection.id(), collection.name(), collection.slug(), collection.status());
    }
}

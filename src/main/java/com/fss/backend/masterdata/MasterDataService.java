package com.fss.backend.masterdata;

import com.fss.backend.catalog.brand.Brand;
import com.fss.backend.catalog.brand.BrandRepository;
import com.fss.backend.catalog.category.Category;
import com.fss.backend.catalog.category.CategoryRepository;
import com.fss.backend.content.collection.ProductCollection;
import com.fss.backend.content.collection.ProductCollectionRepository;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MasterDataService {
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductCollectionRepository collectionRepository;
    private final SizeColorRepository sizeColorRepository;

    public MasterDataService(CategoryRepository categoryRepository, BrandRepository brandRepository, ProductCollectionRepository collectionRepository,
                             SizeColorRepository sizeColorRepository) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.collectionRepository = collectionRepository;
        this.sizeColorRepository = sizeColorRepository;
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
                categoryRepository.listCategories(status, null, 1000, 0).stream().map(this::categoryOption).toList(),
                brandRepository.listBrands(status, null, 1000, 0).stream().map(this::brandOption).toList(),
                collectionRepository.listCollections(publicOnly, status, null, 1000, 0).stream().map(this::collectionOption).toList(),
                List.of(new StaticOption("MALE", "Nam"), new StaticOption("FEMALE", "Nữ"), new StaticOption("UNISEX", "Unisex")),
                List.of(new StaticOption("DRAFT", "Nháp"), new StaticOption("ACTIVE", "Hoạt động"), new StaticOption("INACTIVE", "Ẩn")),
                List.of(new StaticOption("ACTIVE", "Hoạt động"), new StaticOption("INACTIVE", "Ẩn")),
                sizeColorRepository.listSizes(status, null, 1000, 0),
                sizeColorRepository.listColors(status, null, 1000, 0)
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

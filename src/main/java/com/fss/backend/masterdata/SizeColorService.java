package com.fss.backend.masterdata;

import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SizeColorService {
    private final SizeColorRepository repository;
    private final EcommerceSupport support;

    public SizeColorService(SizeColorRepository repository, EcommerceSupport support) {
        this.repository = repository;
        this.support = support;
    }

    public PageResult<SizeOption> listSizes(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        String normalizedKeyword = support.trimToNull(keyword);
        int safeLimit = support.safeLimit(limit);
        List<SizeOption> items = repository.listSizes(normalizedStatus, normalizedKeyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, repository.countSizes(normalizedStatus, normalizedKeyword));
    }

    public SizeOption getSize(UUID id) {
        SizeOption size = repository.findSizeById(id);
        support.require(size != null, "Size not found");
        return size;
    }

    @Transactional
    public UUID createSize(SizeRequest request) {
        UUID id = UUID.randomUUID();
        String value = support.trimRequired(request.value(), "Size");
        support.require(!repository.sizeValueExists(value, null), "Size already exists");
        repository.insertSize(id, value, support.def(support.trimToNull(request.label()), value),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateSize(UUID id, SizeRequest request) {
        support.require(repository.findSizeById(id) != null, "Size not found");
        String value = support.trimRequired(request.value(), "Size");
        support.require(!repository.sizeValueExists(value, id), "Size already exists");
        repository.updateSize(id, value, support.def(support.trimToNull(request.label()), value),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
    }

    @Transactional
    public void deleteSize(UUID id) {
        support.require(repository.findSizeById(id) != null, "Size not found");
        repository.softDeleteSize(id, support.adminId());
    }

    public PageResult<ColorOption> listColors(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        String normalizedKeyword = support.trimToNull(keyword);
        int safeLimit = support.safeLimit(limit);
        List<ColorOption> items = repository.listColors(normalizedStatus, normalizedKeyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, repository.countColors(normalizedStatus, normalizedKeyword));
    }

    public ColorOption getColor(UUID id) {
        ColorOption color = repository.findColorById(id);
        support.require(color != null, "Color not found");
        return color;
    }

    @Transactional
    public UUID createColor(ColorRequest request) {
        UUID id = UUID.randomUUID();
        String name = support.trimRequired(request.name(), "Color name");
        support.require(!repository.colorNameExists(name, null), "Color already exists");
        repository.insertColor(id, name, support.trimToNull(request.colorCode()),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateColor(UUID id, ColorRequest request) {
        support.require(repository.findColorById(id) != null, "Color not found");
        String name = support.trimRequired(request.name(), "Color name");
        support.require(!repository.colorNameExists(name, id), "Color already exists");
        repository.updateColor(id, name, support.trimToNull(request.colorCode()),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
    }

    @Transactional
    public void deleteColor(UUID id) {
        support.require(repository.findColorById(id) != null, "Color not found");
        repository.softDeleteColor(id, support.adminId());
    }
}

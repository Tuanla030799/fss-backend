package com.fss.backend.masterdata;

import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SizeColorService {
    private final SizeColorMapper mapper;
    private final EcommerceSupport support;

    public SizeColorService(SizeColorMapper mapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    public PageResult<SizeOption> listSizes(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        String normalizedKeyword = support.trimToNull(keyword);
        int safeLimit = support.safeLimit(limit);
        List<SizeOption> items = mapper.listSizes(normalizedStatus, normalizedKeyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, mapper.countSizes(normalizedStatus, normalizedKeyword));
    }

    public SizeOption getSize(UUID id) {
        SizeOption size = mapper.findSizeById(id);
        support.require(size != null, "Size not found");
        return size;
    }

    @Transactional
    public UUID createSize(SizeRequest request) {
        UUID id = UUID.randomUUID();
        String value = support.trimRequired(request.value(), "Size");
        support.require(mapper.countSizeValue(value, null) == 0, "Size already exists");
        mapper.insertSize(id, value, support.def(support.trimToNull(request.label()), value),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateSize(UUID id, SizeRequest request) {
        support.require(mapper.findSizeById(id) != null, "Size not found");
        String value = support.trimRequired(request.value(), "Size");
        support.require(mapper.countSizeValue(value, id) == 0, "Size already exists");
        mapper.updateSize(id, value, support.def(support.trimToNull(request.label()), value),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
    }

    @Transactional
    public void deleteSize(UUID id) {
        support.require(mapper.findSizeById(id) != null, "Size not found");
        mapper.softDeleteSize(id, support.adminId());
    }

    public PageResult<ColorOption> listColors(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        String normalizedKeyword = support.trimToNull(keyword);
        int safeLimit = support.safeLimit(limit);
        List<ColorOption> items = mapper.listColors(normalizedStatus, normalizedKeyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, mapper.countColors(normalizedStatus, normalizedKeyword));
    }

    public ColorOption getColor(UUID id) {
        ColorOption color = mapper.findColorById(id);
        support.require(color != null, "Color not found");
        return color;
    }

    @Transactional
    public UUID createColor(ColorRequest request) {
        UUID id = UUID.randomUUID();
        String name = support.trimRequired(request.name(), "Color name");
        support.require(mapper.countColorName(name, null) == 0, "Color already exists");
        mapper.insertColor(id, name, support.trimToNull(request.colorCode()),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateColor(UUID id, ColorRequest request) {
        support.require(mapper.findColorById(id) != null, "Color not found");
        String name = support.trimRequired(request.name(), "Color name");
        support.require(mapper.countColorName(name, id) == 0, "Color already exists");
        mapper.updateColor(id, name, support.trimToNull(request.colorCode()),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
    }

    @Transactional
    public void deleteColor(UUID id) {
        support.require(mapper.findColorById(id) != null, "Color not found");
        mapper.softDeleteColor(id, support.adminId());
    }
}

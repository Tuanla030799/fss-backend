package com.fss.backend.content.faq;

import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.content.html.HtmlContentImageUsageService;
import com.fss.backend.content.html.HtmlSanitizerService;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FaqService {
    private final FaqRepository repository;
    private final EcommerceSupport support;
    private final HtmlSanitizerService htmlSanitizerService;
    private final HtmlContentImageUsageService htmlContentImageUsageService;
    private final FileReferenceService fileReferenceService;

    public FaqService(FaqRepository repository, EcommerceSupport support, HtmlSanitizerService htmlSanitizerService,
                      HtmlContentImageUsageService htmlContentImageUsageService,
                      FileReferenceService fileReferenceService) {
        this.repository = repository;
        this.support = support;
        this.htmlSanitizerService = htmlSanitizerService;
        this.htmlContentImageUsageService = htmlContentImageUsageService;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<Faq> listPublicFaqs(int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<Faq> items = repository.listFaqs(true, null, null, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::forOutput).toList();
        return support.pageResult(items, page, safeLimit, repository.countFaqs(true, null, null));
    }

    public PageResult<Faq> listAdminFaqs(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Faq> items = repository.listFaqs(false, normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::forOutput).toList();
        return support.pageResult(items, page, safeLimit, repository.countFaqs(false, normalizedStatus, keyword));
    }

    public Faq getAdminFaq(UUID id) {
        return forOutput(requireFaq(id));
    }

    @Transactional
    public UUID createFaq(FaqRequest request) {
        UUID id = UUID.randomUUID();
        String answer = sanitizedAnswer(request.answerHtml());
        repository.insertFaq(id, request.question().trim(), answer, htmlContentImageUsageService.filePathList(answer),
                support.nz(request.sortOrder()), support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.adminId());
        htmlContentImageUsageService.activateImages(answer);
        return id;
    }

    @Transactional
    public void updateFaq(UUID id, FaqRequest request) {
        Faq existing = requireFaq(id);
        String answer = sanitizedAnswer(request.answerHtml());
        repository.updateFaq(id, request.question().trim(), answer, htmlContentImageUsageService.filePathList(answer),
                support.nz(request.sortOrder()), support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.adminId());
        htmlContentImageUsageService.activateImages(answer);
        fileReferenceService.releaseRemovedHtmlImages(existing.answerHtml(), answer);
    }

    @Transactional
    public void deleteFaq(UUID id) {
        Faq existing = requireFaq(id);
        repository.softDeleteFaq(id, support.adminId());
        fileReferenceService.releaseRemovedHtmlImages(existing.answerHtml(), null);
    }

    private Faq requireFaq(UUID id) {
        Faq faq = repository.findFaqById(id);
        support.requireFound(faq != null, ErrorCode.FAQ_NOT_FOUND, "FAQ not found");
        return faq;
    }

    /** An answer that is empty once unsafe markup is stripped (e.g. only a script) is rejected like a blank one. */
    private String sanitizedAnswer(String html) {
        String sanitized = htmlSanitizerService.sanitize(html);
        support.require(hasContent(sanitized), ErrorCode.VALIDATION_ERROR, "FAQ answer must not be blank");
        return sanitized;
    }

    private static boolean hasContent(String html) {
        if (html == null || html.isBlank()) return false;
        Element body = Jsoup.parseBodyFragment(html).body();
        return !body.text().isBlank() || !body.select("img").isEmpty();
    }

    private Faq forOutput(Faq faq) {
        return new Faq(faq.id(), faq.question(), htmlContentImageUsageService.rewriteForOutput(faq.answerHtml()),
                faq.sortOrder(), faq.status(), faq.createdAt(), faq.updatedAt());
    }
}

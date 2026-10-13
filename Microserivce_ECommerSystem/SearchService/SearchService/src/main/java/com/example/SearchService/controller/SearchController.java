package com.example.SearchService.controller;

import com.example.SearchService.document.ProductDocument;
import com.example.SearchService.repository.ProductSearchRepository;
import com.example.SearchService.service.ProductIndexSyncService;
import com.example.SearchService.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchRepository repository;
    private final ProductSearchService searchService;
    private final ProductIndexSyncService syncService;

    /** Tìm sản phẩm theo từ khóa, kết quả xếp theo độ liên quan. */
    @GetMapping("/products")
    public List<ProductDocument> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return searchService.search(query, page, size);
    }

    @GetMapping("/all")
    public org.springframework.data.domain.Page<ProductDocument> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // Trả về dữ liệu đã được phân trang (Ví dụ: 50 sản phẩm/trang) thay vì lôi cả 10.000 cái ra gây sập SERVER
        return repository.findAll(org.springframework.data.domain.PageRequest.of(page, size));
    }

    /** Nạp lại toàn bộ chỉ mục từ Product Service (Gateway chỉ cho ADMIN gọi). */
    @GetMapping("/sync")
    public String syncFromProductService() {
        try {
            int count = syncService.fullSync();
            return "Đã đồng bộ " + count + " sản phẩm vào Elasticsearch thành công!";
        } catch (Exception e) {
            return "Lỗi đồng bộ: " + e.getMessage();
        }
    }
}

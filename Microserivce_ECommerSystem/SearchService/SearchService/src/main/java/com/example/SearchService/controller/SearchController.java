package com.example.SearchService.controller;

import com.example.SearchService.document.ProductDocument;
import com.example.SearchService.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchRepository repository;

    @GetMapping("/products")
    public List<ProductDocument> searchProducts(@RequestParam String query) {
        return repository.findByNameContaining(query);
    }

    @GetMapping("/all")
    public org.springframework.data.domain.Page<ProductDocument> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // Trả về dữ liệu đã được phân trang (Ví dụ: 50 sản phẩm/trang) thay vì lôi cả 10.000 cái ra gây sập SERVER
        return repository.findAll(org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/sync")
    public String syncFromProductService() {
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            // Lấy data từ ProductService (cổng 8082)
            ProductDocument[] products = restTemplate.getForObject("http://localhost:8082/api/products/all", ProductDocument[].class);
            if (products != null) {
                int count = 0;
                for (ProductDocument p : products) {
                    repository.save(p);
                    count++;
                }
                return "Đã đồng bộ thủ công " + count + " sản phẩm vào Elasticsearch thành công!";
            }
        } catch (Exception e) {
            return "Lỗi đồng bộ: " + e.getMessage();
        }
        return "Không có sản phẩm nào để đồng bộ.";
    }
}

package com.example.SearchService.service;

import com.example.SearchService.document.ProductDocument;
import com.example.SearchService.repository.ProductSearchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Giữ cho chỉ mục tìm kiếm khớp với dữ liệu thật ở Product Service, bằng 2 đường:
 *  - Từng thay đổi: Product Service phát sự kiện qua Kafka (tạo / sửa / xóa / đổi giá) -> applyEvent().
 *  - Đối soát toàn bộ: fullSync() chạy lúc khởi động (nếu chỉ mục trống), định kỳ, và khi admin gọi
 *    /api/search/sync — để bù cho những sự kiện bị mất (Kafka chết, service tắt lâu...).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexSyncService {

    private static final String ACTIVE = "ACTIVE";

    private final ProductSearchRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${PRODUCT_SERVICE_URL:http://localhost:8082}")
    private String productServiceUrl;

    /** Xử lý 1 sự kiện sản phẩm từ Kafka. An toàn khi nhận lại cùng một sự kiện nhiều lần. */
    public void applyEvent(String message) throws Exception {
        JsonNode json = objectMapper.readTree(message);
        JsonNode idNode = json.get("id");
        if (idNode == null || idNode.asText().isBlank()) {
            log.warn("Bỏ qua sự kiện sản phẩm không có id: {}", message);
            return;
        }
        String id = idNode.asText();

        if (json.path("deleted").asBoolean(false)) {
            repository.deleteById(id);
            log.info("Removed product from search index: {}", id);
            return;
        }

        ProductDocument product = objectMapper.treeToValue(json, ProductDocument.class);
        if (isSearchable(product)) {
            repository.save(product);
            log.info("Synced product to Elasticsearch: {}", id);
        } else {
            // Sản phẩm ngừng kinh doanh / nháp: không cho hiện trong kết quả tìm kiếm
            repository.deleteById(id);
            log.info("Removed non-active product from search index: {}", id);
        }
    }

    /**
     * Nạp lại toàn bộ từ Product Service: thêm/cập nhật sản phẩm đang bán, gỡ sản phẩm không còn.
     * @return số sản phẩm đang có trong chỉ mục sau khi đồng bộ
     */
    public synchronized int fullSync() {
        ProductDocument[] all = restTemplate().getForObject(productServiceUrl + "/api/products/all", ProductDocument[].class);
        if (all == null) {
            throw new IllegalStateException("Product Service không trả dữ liệu");
        }

        List<ProductDocument> active = new ArrayList<>();
        Set<String> activeIds = new HashSet<>();
        for (ProductDocument product : all) {
            if (product.getId() != null && isSearchable(product)) {
                active.add(product);
                activeIds.add(product.getId());
            }
        }
        repository.saveAll(active);

        // Gỡ những gì còn trong chỉ mục nhưng không còn bán ở Product Service
        List<String> stale = new ArrayList<>();
        repository.findAll().forEach(doc -> {
            if (!activeIds.contains(doc.getId())) stale.add(doc.getId());
        });
        if (!stale.isEmpty()) {
            repository.deleteAllById(stale);
        }

        log.info("Full sync done: {} sản phẩm trong chỉ mục, gỡ {} sản phẩm cũ", active.size(), stale.size());
        return active.size();
    }

    /** Lần đầu chạy (hoặc sau khi đổi sang index mới) chỉ mục trống: tự nạp dữ liệu. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupIfEmpty() {
        try {
            if (repository.count() == 0) {
                fullSync();
            }
        } catch (Exception e) {
            log.warn("Chưa nạp được dữ liệu tìm kiếm lúc khởi động (sẽ thử lại theo lịch): {}", e.getMessage());
        }
    }

    /** Đối soát định kỳ, mặc định 30 phút một lần. */
    @Scheduled(fixedDelayString = "${search.full-sync.interval-ms:1800000}",
            initialDelayString = "${search.full-sync.initial-delay-ms:300000}")
    public void scheduledSync() {
        try {
            fullSync();
        } catch (Exception e) {
            log.warn("Đối soát chỉ mục tìm kiếm thất bại (sẽ thử lại lần sau): {}", e.getMessage());
        }
    }

    private boolean isSearchable(ProductDocument product) {
        return ACTIVE.equals(product.getProductStatus());
    }

    // Có timeout: Product Service chết hay treo thì báo lỗi nhanh thay vì treo luồng này
    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}

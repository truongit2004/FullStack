package com.example.SearchService;

import com.example.SearchService.document.ProductDocument;
import com.example.SearchService.repository.ProductSearchRepository;
import com.example.SearchService.service.ProductIndexSyncService;
import com.example.SearchService.service.ProductSearchService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra tìm kiếm và đồng bộ chỉ mục trên Elasticsearch THẬT.
 *
 * Cần Elasticsearch đang chạy ở localhost:9200. Dùng index riêng "products_search_test"
 * (không đụng index "products_v2" của ứng dụng). Product Service được giả lập bằng máy chủ HTTP nhỏ.
 */
@SpringBootTest(properties = {
        "search.index-name=products_search_test",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "search.full-sync.initial-delay-ms=3600000",
        "search.full-sync.interval-ms=3600000"
})
class ProductSearchTest {

    static HttpServer productServiceStub;
    static volatile String productsJson = "[]";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        productServiceStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        productServiceStub.createContext("/api/products/all", exchange -> {
            byte[] body = productsJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        productServiceStub.start();
        registry.add("PRODUCT_SERVICE_URL", () -> "http://localhost:" + productServiceStub.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        productServiceStub.stop(0);
    }

    @Autowired ProductSearchService searchService;
    @Autowired ProductIndexSyncService syncService;
    @Autowired ProductSearchRepository repository;
    @Autowired ElasticsearchOperations operations;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                product("1", "Áo thun nam cổ tròn", "Chất liệu cotton thoáng mát", "15"),
                product("2", "Áo sơ mi nữ công sở", "Vải lụa mềm, dễ phối với quần tây", "25"),
                product("3", "Quần jean nam ống đứng", "Phối đẹp với áo thun", "30"),
                product("4", "iPhone 15 Pro Max", "Điện thoại Apple chip A17", "1200"),
                product("5", "Samsung Galaxy S24", "Điện thoại Android màn hình lớn", "900"),
                product("6", "Giày thể thao nữ", "Đế êm, nhẹ", "45")
        ));
        refresh();
    }

    private ProductDocument product(String id, String name, String description, String price) {
        return ProductDocument.builder().id(id).name(name).description(description)
                .price(new BigDecimal(price)).productStatus("ACTIVE").categoryName("Test").build();
    }

    // Elasticsearch cần "refresh" thì dữ liệu vừa ghi mới tìm thấy được (bình thường tự làm mỗi giây)
    private void refresh() {
        operations.indexOps(ProductDocument.class).refresh();
    }

    private List<String> ids(String keyword) {
        return searchService.search(keyword, 0, 50).stream().map(ProductDocument::getId).toList();
    }

    // ── Chất lượng tìm kiếm ──────────────────────────────────────────────────

    @Test
    void findsByWordsInName() {
        assertEquals(List.of("1"), ids("áo thun nam"));
    }

    @Test
    void worksWithoutVietnameseAccents_andIgnoresCase() {
        assertEquals("1", ids("ao thun").get(0), "Gõ không dấu vẫn phải ra 'Áo thun'");
        assertEquals("1", ids("AO THUN").get(0));
        assertTrue(ids("quan jean").contains("3"));
        assertTrue(ids("giay the thao").contains("6"));
    }

    @Test
    void matchInName_ranksAboveMatchInDescriptionOnly() {
        // "áo thun": sản phẩm 1 có trong TÊN, sản phẩm 3 chỉ có trong MÔ TẢ
        List<String> result = ids("áo thun");
        assertEquals(List.of("1", "3"), result, "Khớp ở tên phải đứng trước khớp ở mô tả");
    }

    @Test
    void wordOrderDoesNotMatter_unlikeSqlLike() {
        assertEquals(List.of("1"), ids("nam thun áo"));
    }

    @Test
    void toleratesTypos_inLongerWords() {
        assertEquals(List.of("4"), ids("iphnoe"), "Gõ nhầm thứ tự chữ vẫn ra iPhone");
        assertEquals(List.of("5"), ids("samsumg"));
    }

    @Test
    void matchesWhileTyping_lastWordIncomplete() {
        assertEquals("1", ids("ao th").get(0));
        assertEquals(List.of("4"), ids("ipho"));
    }

    @Test
    void searchesDescriptionToo() {
        assertEquals(List.of("4", "5"), ids("điện thoại").stream().sorted().toList());
    }

    @Test
    void allWordsMustMatch_soUnrelatedProductsAreNotReturned() {
        assertTrue(ids("áo khoác lông").isEmpty(), "Không có sản phẩm nào chứa đủ các từ này");
        assertTrue(ids("tủ lạnh").isEmpty());
    }

    @Test
    void blankKeyword_returnsEverything() {
        assertEquals(6, ids("  ").size());
    }

    // ── Đồng bộ từng thay đổi (sự kiện Kafka) ────────────────────────────────

    @Test
    void events_createUpdateDelete_keepIndexInSync() throws Exception {
        syncService.applyEvent("""
                {"id":"7","name":"Balo du lịch","description":"Chống nước","price":40,"onSale":false,
                 "productStatus":"ACTIVE","categoryName":"Phụ kiện","stockQuantity":12,"createdAt":"2026-10-12T10:00:00"}""");
        refresh();
        assertEquals(List.of("7"), ids("balo"));

        // Đổi giá và bật giảm giá (ví dụ khuyến mãi ngày lễ)
        syncService.applyEvent("""
                {"id":"7","name":"Balo du lịch","description":"Chống nước","price":28,"originalPrice":40,
                 "onSale":true,"discountPercentage":30,"productStatus":"ACTIVE"}""");
        refresh();
        ProductDocument updated = searchService.search("balo", 0, 10).get(0);
        assertEquals(0, new BigDecimal("28").compareTo(updated.getPrice()));
        assertEquals(Boolean.TRUE, updated.getOnSale());
        assertEquals(30, updated.getDiscountPercentage());

        // Ngừng kinh doanh -> biến mất khỏi kết quả
        syncService.applyEvent("{\"id\":\"7\",\"name\":\"Balo du lịch\",\"productStatus\":\"INACTIVE\"}");
        refresh();
        assertTrue(ids("balo").isEmpty());

        // Xóa
        syncService.applyEvent("{\"id\":\"1\",\"deleted\":true}");
        refresh();
        assertFalse(ids("áo thun").contains("1"));

        // Nhận lại sự kiện xóa lần nữa không lỗi
        assertDoesNotThrow(() -> syncService.applyEvent("{\"id\":\"1\",\"deleted\":true}"));
    }

    // ── Đối soát toàn bộ ─────────────────────────────────────────────────────

    @Test
    void fullSync_addsActive_skipsInactive_andRemovesStale() {
        // Product Service hiện chỉ còn 3 sản phẩm: 1 (đổi tên), 8 (mới), 9 (đã ngừng bán)
        productsJson = """
                [{"id":"1","name":"Áo thun nam cổ tim","price":16,"productStatus":"ACTIVE","onSale":false},
                 {"id":"8","name":"Mũ lưỡi trai","price":9,"productStatus":"ACTIVE","onSale":false},
                 {"id":"9","name":"Ví da cũ","price":5,"productStatus":"INACTIVE","onSale":false}]""";

        int count = syncService.fullSync();
        refresh();

        assertEquals(2, count);
        assertEquals(2, repository.count(), "Các sản phẩm 2-6 không còn ở Product Service phải bị gỡ");
        assertEquals(List.of("1"), ids("cổ tim"));
        assertEquals(List.of("8"), ids("mu luoi trai"));
        assertTrue(ids("ví da").isEmpty(), "Sản phẩm ngừng bán không được lập chỉ mục");
    }
}

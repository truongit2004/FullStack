package com.example.ProductService;

import com.example.ProductService.client.InventoryClient;
import com.example.ProductService.dto.ProductRequestDTO;
import com.example.ProductService.dto.ProductResponseDTO;
import com.example.ProductService.entity.CategoryEntity;
import com.example.ProductService.repository.CategoryRepository;
import com.example.ProductService.repository.ProductRepository;
import com.example.ProductService.service.FileStorageService;
import com.example.ProductService.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra: mọi thay đổi sản phẩm (tạo, sửa, xóa mềm, xóa cứng) đều báo cho Search Service qua Kafka,
 * đúng định dạng Search Service đọc được, và KHÔNG báo khi thao tác bị rollback.
 *
 * Cần MySQL đang chạy ở localhost; dùng database riêng "product_service_search_test".
 * Kafka, Inventory Service và nơi lưu ảnh được giả lập.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/product_service_search_test?createDatabaseIfNotExist=true",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
class ProductSearchSyncTest {

    private static final String TOPIC = "product-topic";

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean InventoryClient inventoryClient;
    @MockitoBean FileStorageService fileStorageService;

    String categoryId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        categoryId = categoryRepository.save(CategoryEntity.builder().name("Thời trang").build()).getId();
        when(fileStorageService.save(any())).thenReturn("ao-thun.png");
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private ProductRequestDTO request(String name, String price) {
        return ProductRequestDTO.builder().name(name).description("Cotton").price(new BigDecimal(price))
                .categoryId(categoryId).stockQuantity(10).build();
    }

    private JsonNode lastEvent(String productId) throws Exception {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, atLeastOnce()).send(eq(TOPIC), eq(productId), payload.capture());
        return objectMapper.readTree(payload.getValue());
    }

    @Test
    void create_publishesProductInTheShapeSearchServiceReads() throws Exception {
        ProductResponseDTO created = productService.create(request("Áo thun nam", "15"));

        JsonNode event = lastEvent(created.getId());
        assertEquals(created.getId(), event.get("id").asText());
        assertEquals("Áo thun nam", event.get("name").asText());
        assertEquals("ACTIVE", event.get("productStatus").asText());
        assertEquals("ao-thun.png", event.get("imageUrl").asText());
        assertEquals(0, new BigDecimal("15").compareTo(event.get("price").decimalValue()));
        // Search Service (và FE) đọc trường "onSale", không phải "isOnSale"
        assertTrue(event.has("onSale"));
        assertFalse(event.has("deleted"));
    }

    @Test
    void update_publishesNewNameAndPrice() throws Exception {
        ProductResponseDTO created = productService.create(request("Áo thun nam", "15"));
        clearInvocations(kafkaTemplate);

        productService.update(created.getId(), request("Áo thun nam cổ tim", "18"));

        JsonNode event = lastEvent(created.getId());
        assertEquals("Áo thun nam cổ tim", event.get("name").asText());
        assertEquals(0, new BigDecimal("18").compareTo(event.get("price").decimalValue()));
    }

    @Test
    void softDelete_andHardDelete_publishDeletion() throws Exception {
        ProductResponseDTO soft = productService.create(request("Quần jean", "30"));
        ProductResponseDTO hard = productService.create(request("Mũ lưỡi trai", "9"));
        clearInvocations(kafkaTemplate);

        productService.softDelete(soft.getId());
        JsonNode softEvent = lastEvent(soft.getId());
        assertTrue(softEvent.get("deleted").asBoolean());
        assertEquals(soft.getId(), softEvent.get("id").asText());

        productService.hardDelete(hard.getId());
        assertTrue(lastEvent(hard.getId()).get("deleted").asBoolean());
    }

    @Test
    void create_thatRollsBack_publishesNothing() {
        doThrow(new RuntimeException("inventory-service down")).when(inventoryClient).createInventory(anyString(), any());

        assertThrows(RuntimeException.class, () -> productService.create(request("Sản phẩm lỗi", "1")));

        assertEquals(0, productRepository.count(), "Sản phẩm phải được rollback");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void kafkaDown_doesNotBreakProductChanges() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("kafka down"));

        ProductResponseDTO created = assertDoesNotThrow(() -> productService.create(request("Giày thể thao", "45")));

        assertTrue(productRepository.existsById(created.getId()), "Kafka chết không được làm hỏng việc tạo sản phẩm");
    }
}

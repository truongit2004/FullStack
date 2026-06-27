package com.example.ProductService.service.impl;

import com.example.ProductService.client.InventoryClient;
import com.example.ProductService.dto.ProductFilterDTO;
import com.example.ProductService.dto.ProductRequestDTO;
import com.example.ProductService.dto.ProductResponseDTO;
import com.example.ProductService.entity.ProductEntity;
import com.example.ProductService.entity.ProductStatus;
import com.example.ProductService.exception.ResourceNotFoundException;
import com.example.ProductService.mapper.ProductMapper;
import com.example.ProductService.repository.ProductRepository;
import com.example.ProductService.service.FileStorageService;
import com.example.ProductService.service.ProductService;
import com.example.ProductService.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String DEFAULT_SORT_FIELD = "createdAt";
    private static final String PRODUCT_TOPIC = "product-topic";

    private final ProductRepository  productRepository;
    private final FileStorageService fileStorageService;
    private final InventoryClient    inventoryClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProductResponseDTO create(ProductRequestDTO dto) {
        log.info("Creating product: name={}", dto.getName());
        String imageUrl       = fileStorageService.save(dto.getImageUrl());
        ProductEntity entity  = ProductMapper.toEntity(dto, imageUrl);
        ProductEntity saved   = productRepository.save(entity);

        try {
            inventoryClient.createInventory(saved.getId(), dto.getStockQuantity());
            log.info("Inventory created for productId={}", saved.getId());
        } catch (Exception e) {
            log.error("Inventory creation failed for productId={}: {}", saved.getId(), e.getMessage());
            throw new RuntimeException("Product saved but inventory setup failed: " + e.getMessage());
        }

        ProductResponseDTO response = ProductMapper.toResponse(saved);
        
        // Sync to Search Service via Kafka
        publishProductToKafka(response, saved.getId());

        return response;
    }

    @Override
    public Page<ProductResponseDTO> filter(ProductFilterDTO filter, int page, int size) {
        Pageable pageable = buildPageable(filter.getSortBy(), filter.getSortDir(), page, size);
        Page<ProductResponseDTO> products = productRepository.findAll(ProductSpecification.filter(filter), pageable)
                .map(ProductMapper::toResponse);

        // Enrich with stock info
        products.forEach(this::enrichWithStockInfo);

        return products;
    }

    @Override
    public ProductResponseDTO getById(String id) {
        ProductResponseDTO response = ProductMapper.toResponse(findActiveById(id));
        enrichWithStockInfo(response);
        return response;
    }

    @Override
    @Transactional
    public ProductResponseDTO update(String id, ProductRequestDTO dto) {
        log.info("Updating product: id={}", id);
        ProductEntity entity = findActiveById(id);
        updateImage(entity, dto);
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setPrice(dto.getPrice());
        entity.setCategoryId(dto.getCategoryId());
        return ProductMapper.toResponse(productRepository.save(entity));
    }

    @Override
    @Transactional
    public void softDelete(String id) {
        log.info("Soft deleting product: id={}", id);
        ProductEntity entity = findActiveById(id);
        entity.setDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setProductStatus(ProductStatus.INACTIVE);
        productRepository.save(entity);
    }

    @Override
    @Transactional
    public void hardDelete(String id) {
        log.info("Hard deleting product: id={}", id);
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        fileStorageService.delete(entity.getImageUrl());
        productRepository.delete(entity);
    }

    @Override
    public void decreaseStock(String id, Integer quantity) {
        inventoryClient.decreaseStock(id, quantity);
    }

    @Override
    public void increaseStock(String id, Integer quantity) {
        inventoryClient.increaseStock(id, quantity);
    }

    @Override
    public List<ProductResponseDTO> getAllIncludeDeleted() {
        return productRepository.findAll().stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponseDTO> getAllDeleted() {
        return productRepository.findAllByDeletedTrue().stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void enrichWithStockInfo(ProductResponseDTO product) {
        try {
            Object inventoryObj = inventoryClient.getStock(product.getId());
            if (inventoryObj instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) inventoryObj;
                if (map.get("stockQuantity") != null) {
                    product.setStockQuantity((Integer) map.get("stockQuantity"));
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch stock for product {}: {}", product.getId(), e.getMessage());
            product.setStockQuantity(0);
        }
    }

    private ProductEntity findActiveById(String id) {
        return productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found or deleted: " + id));
    }

    private Pageable buildPageable(String sortBy, String sortDir, int page, int size) {
        String field         = (sortBy != null && !sortBy.isBlank()) ? sortBy : DEFAULT_SORT_FIELD;
        Sort.Direction dir   = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private void updateImage(ProductEntity entity, ProductRequestDTO dto) {
        if (dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) {
            fileStorageService.delete(entity.getImageUrl());
            entity.setImageUrl(fileStorageService.save(dto.getImageUrl()));
        } else if (Boolean.TRUE.equals(dto.getDeleteImage())) {
            fileStorageService.delete(entity.getImageUrl());
            entity.setImageUrl(null);
        }
    }

    private void publishProductToKafka(ProductResponseDTO response, String productId) {
        try {
            String message = objectMapper.writeValueAsString(response);
            kafkaTemplate.send(PRODUCT_TOPIC, productId, message);
            log.info("Sent product to Kafka for SearchService: {}", productId);
        } catch (Exception e) {
            log.error("Failed to send product to Kafka for id {}: {}", productId, e.getMessage());
        }
    }
}
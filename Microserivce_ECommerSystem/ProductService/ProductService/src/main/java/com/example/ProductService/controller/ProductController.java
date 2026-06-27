package com.example.ProductService.controller;

import com.example.ProductService.dto.ProductFilterDTO;
import com.example.ProductService.dto.ProductRequestDTO;
import com.example.ProductService.dto.ProductResponseDTO;
import com.example.ProductService.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ProductController {

    private final ProductService productService;

    // ── Serve ảnh sản phẩm ──────────────────────────────────────────
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path uploadDir = Paths.get("uploads/products").toAbsolutePath().normalize();
            Path filePath = uploadDir.resolve(filename).normalize();

            // Chặn path traversal attack
            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> create(
            @ModelAttribute @Valid ProductRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.create(dto));
    }

    @GetMapping("/filter")
    public ResponseEntity<Map<String, Object>> filter(
            @ModelAttribute ProductFilterDTO filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ProductResponseDTO> result = productService.filter(filter, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());
        response.put("currentPage", result.getNumber());
        response.put("totalPages", result.getTotalPages());
        response.put("totalElements", result.getTotalElements());
        response.put("pageSize", result.getSize());
        response.put("first", result.isFirst());
        response.put("last", result.isLast());

        return ResponseEntity.ok(response);
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> update(
            @PathVariable String id,
            @ModelAttribute @Valid ProductRequestDTO dto) {
        return ResponseEntity.ok(productService.update(id, dto));
    }

    @DeleteMapping("/soft/{id}")
    public ResponseEntity<String> softDelete(@PathVariable String id) {
        productService.softDelete(id);
        return ResponseEntity.ok("Xóa mềm thành công");
    }

    // DELETE cứng
    @DeleteMapping("/hard/{id}")
    public ResponseEntity<String> hardDelete(@PathVariable String id) {
        productService.hardDelete(id);
        return ResponseEntity.ok("Xóa cứng thành công");
    }

    @GetMapping("/all")
    public ResponseEntity<List<ProductResponseDTO>> getAllIncludeDeleted() {
        return ResponseEntity.ok(productService.getAllIncludeDeleted());
    }

    // Lấy chỉ những sản phẩm đã xóa mềm
    @GetMapping("/deleted")
    public ResponseEntity<List<ProductResponseDTO>> getAllDeleted() {
        return ResponseEntity.ok(productService.getAllDeleted());
    }

    @PutMapping("/{id}/stock/decrease")
    public ResponseEntity<Void> decreaseStock(@PathVariable String id,
            @RequestParam Integer quantity) {
        productService.decreaseStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/stock/increase")
    public ResponseEntity<Void> increaseStock(@PathVariable String id,
            @RequestParam Integer quantity) {
        productService.increaseStock(id, quantity);
        return ResponseEntity.ok().build();
    }
}

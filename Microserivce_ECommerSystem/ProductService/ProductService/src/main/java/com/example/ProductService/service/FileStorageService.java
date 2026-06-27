package com.example.ProductService.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    // Thư mục lưu ảnh (tạo tự động nếu chưa có)
    private final Path uploadDir = Paths.get("uploads/products");

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(uploadDir);
    }

    public String save(MultipartFile file) {
        try {
            // Validate
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("File ảnh không được để trống");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new RuntimeException("Chỉ chấp nhận file ảnh");
            }

            // Tạo tên file unique
            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + "." + ext;

            // Lưu file
            Path target = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // URL trả về client, khớp với endpoint trong ProductController (đã được Gateway route)
            return "/api/products/files/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi lưu ảnh: " + e.getMessage());
        }
    }

    public void delete(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isBlank()) return;

            // Lấy tên file từ imageUrl (ví dụ: /api/products/files/abc.jpg -> abc.jpg)
            String filename = Paths.get(imageUrl).getFileName().toString();
            Path filePath = uploadDir.resolve(filename);
            
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi xóa ảnh: " + e.getMessage());
        }
    }
}
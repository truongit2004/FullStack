package com.example.ProductService.service.impl;

import com.example.ProductService.dto.CategoryDTO;
import com.example.ProductService.dto.ProductResponseDTO;
import com.example.ProductService.entity.CategoryEntity;
import com.example.ProductService.mapper.ProductMapper;
import com.example.ProductService.repository.CategoryRepository;
import com.example.ProductService.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public CategoryDTO create(CategoryDTO dto) {
        if (categoryRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Category name already exists: " + dto.getName());
        }

        CategoryEntity entity = CategoryEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
        CategoryEntity saved = categoryRepository.save(entity);
        return mapToDto(saved, false);
    }

    @Override
    public List<CategoryDTO> getAll() {
        return categoryRepository.findAll().stream()
                .map(entity -> mapToDto(entity, false))
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDTO getById(String id) {
        CategoryEntity entity = categoryRepository.findWithProductsById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return mapToDto(entity, true);
    }

    @Override
    public CategoryDTO update(String id, CategoryDTO dto) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        
        if (!entity.getName().equals(dto.getName()) && categoryRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Category name already exists: " + dto.getName());
        }

        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        CategoryEntity updated = categoryRepository.save(entity);
        return mapToDto(updated, false);
    }

    @Override
    public void delete(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new RuntimeException("Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private CategoryDTO mapToDto(CategoryEntity entity, boolean includeProducts) {
        CategoryDTO.CategoryDTOBuilder builder = CategoryDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription());

        if (includeProducts && entity.getProducts() != null) {
            List<ProductResponseDTO> products = entity.getProducts().stream()
                    .map(ProductMapper::toResponse)
                    .collect(Collectors.toList());
            builder.products(products);
        }

        return builder.build();
    }
}

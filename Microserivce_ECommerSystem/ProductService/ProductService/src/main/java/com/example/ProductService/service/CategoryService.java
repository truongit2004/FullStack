package com.example.ProductService.service;

import com.example.ProductService.dto.CategoryDTO;
import java.util.List;

public interface CategoryService {
    CategoryDTO create(CategoryDTO dto);
    List<CategoryDTO> getAll();
    CategoryDTO getById(String id);
    CategoryDTO update(String id, CategoryDTO dto);
    void delete(String id);
}

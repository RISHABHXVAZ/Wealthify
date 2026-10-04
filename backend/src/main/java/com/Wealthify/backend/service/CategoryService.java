package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.CategoryDto;
import com.Wealthify.backend.dto.CategoryRequest;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryDto::fromEntity)
                .toList();
    }

    public CategoryDto createCategory(CategoryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Category request must not be null");
        }

        String normalizedName = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            log.warn("Attempt to create duplicate category: {}", normalizedName);
            throw new IllegalArgumentException("Category with name '" + normalizedName + "' already exists.");
        }

        String normalizedType = request.getType().trim().toUpperCase(Locale.ROOT);
        boolean essential = request.getIsEssential() != null
                ? request.getIsEssential()
                : ("NEED".equals(normalizedType) || "INVESTMENT".equals(normalizedType));

        Category newCategory = Category.builder()
                .name(normalizedName)
                .type(normalizedType)
                .isEssential(essential)
                .build();

        Category saved = categoryRepository.save(newCategory);
        log.info("Created new category: id={}, name={}, type={}, isEssential={}",
                saved.getId(), saved.getName(), saved.getType(), saved.getIsEssential());

        return CategoryDto.fromEntity(saved);
    }
}
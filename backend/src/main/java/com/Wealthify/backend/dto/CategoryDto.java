package com.Wealthify.backend.dto;

import com.Wealthify.backend.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Integer id;
    private String name;
    private String type;
    private Boolean isEssential;

    public static CategoryDto fromEntity(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .isEssential(category.getIsEssential())
                .build();
    }
}

package com.Wealthify.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 50, message = "Category name must be between 1 and 50 characters")
    private String name;

    @NotBlank(message = "Category type is required")
    @Pattern(regexp = "^(?i)(NEED|WANT|INVESTMENT)$", message = "Category type must be NEED, WANT, or INVESTMENT")
    private String type;

    private Boolean isEssential;
}

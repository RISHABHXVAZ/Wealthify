package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.CategoryDto;
import com.Wealthify.backend.dto.CategoryRequest;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.exception.GlobalExceptionHandler;
import com.Wealthify.backend.repository.CategoryRepository;
import com.Wealthify.backend.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategorySecurityTest {

    private MockMvc mockMvc;

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;
    private CategoryController categoryController;

    private Category seededFoodCategory;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
        categoryController = new CategoryController(categoryService);

        mockMvc = MockMvcBuilders.standaloneSetup(categoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        seededFoodCategory = Category.builder()
                .id(1)
                .name("Food")
                .type("NEED")
                .isEssential(true)
                .build();
    }

    // ─── Test 1: Existing ID and Category Overwriting Prohibited ─────────────

    @Test
    @DisplayName("SEC-06: Attempt to overwrite existing category via ID and name is rejected with 400")
    void testCannotOverwriteExistingCategoryWithSameName() throws Exception {
        when(categoryRepository.existsByNameIgnoreCase("Food")).thenReturn(true);

        String attackPayload = """
            {
              "id": 1,
              "name": "Food",
              "type": "WANT",
              "isEssential": false
            }
            """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attackPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category with name 'Food' already exists."));

        // Verify save was NEVER called, protecting the seeded category
        verify(categoryRepository, never()).save(any(Category.class));
        assertThat(seededFoodCategory.getType()).isEqualTo("NEED");
        assertThat(seededFoodCategory.getIsEssential()).isTrue();
    }

    @Test
    @DisplayName("SEC-06: Supplying client ID in JSON is ignored and does NOT update existing category ID")
    void testClientIdInJsonIsIgnoredAndDoesNotUpdateExistingRecord() throws Exception {
        when(categoryRepository.existsByNameIgnoreCase("Fitness")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            return Category.builder()
                    .id(100) // DB assigns a new ID
                    .name(c.getName())
                    .type(c.getType())
                    .isEssential(c.getIsEssential())
                    .build();
        });

        // Attacker attempts to hijack existing ID 1
        String attackPayload = """
            {
              "id": 1,
              "name": "Fitness",
              "type": "NEED",
              "isEssential": true
            }
            """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attackPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("Fitness"));

        // Capture what was passed to categoryRepository.save
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());

        Category savedCategory = captor.getValue();
        // The ID passed to JPA MUST be null to force an INSERT, never an UPDATE
        assertThat(savedCategory.getId()).isNull();
        assertThat(savedCategory.getName()).isEqualTo("Fitness");
    }

    // ─── Test 2 & 3: Type and Name Tampering Prohibited ───────────────────────

    @Test
    @DisplayName("SEC-06: Attempt to tamper with existing category type (NEED -> WANT) is blocked")
    void testCannotTamperCategoryType() {
        when(categoryRepository.existsByNameIgnoreCase("Food")).thenReturn(true);

        CategoryRequest tamperRequest = CategoryRequest.builder()
                .name("Food")
                .type("WANT")
                .isEssential(false)
                .build();

        assertThatThrownBy(() -> categoryService.createCategory(tamperRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category with name 'Food' already exists");

        verify(categoryRepository, never()).save(any(Category.class));
        assertThat(seededFoodCategory.getType()).isEqualTo("NEED");
    }

    @Test
    @DisplayName("SEC-06: Case-insensitive duplicate check prevents creating duplicate category variations")
    void testCaseInsensitiveDuplicateCheck() {
        when(categoryRepository.existsByNameIgnoreCase("food")).thenReturn(true);

        CategoryRequest tamperRequest = CategoryRequest.builder()
                .name("  food  ")
                .type("WANT")
                .build();

        assertThatThrownBy(() -> categoryService.createCategory(tamperRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category with name 'food' already exists");

        verify(categoryRepository, never()).save(any(Category.class));
    }

    // ─── Test 4: Validation Constraints ──────────────────────────────────────

    @Test
    @DisplayName("Validation: Blank or missing category name is rejected with 400")
    void testBlankNameRejected() throws Exception {
        String invalidPayload = """
            {
              "name": "   ",
              "type": "NEED"
            }
            """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: Category name is required"));

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Validation: Invalid category type is rejected with 400")
    void testInvalidTypeRejected() throws Exception {
        String invalidPayload = """
            {
              "name": "Crypto",
              "type": "RANDOM_TYPE"
            }
            """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("type: Category type must be NEED, WANT, or INVESTMENT"));

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Validation: Excessive category name length (>50 chars) is rejected with 400")
    void testExcessiveNameLengthRejected() throws Exception {
        String longName = "A".repeat(51);
        String invalidPayload = String.format("""
            {
              "name": "%s",
              "type": "WANT"
            }
            """, longName);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: Category name must be between 1 and 50 characters"));

        verify(categoryRepository, never()).save(any());
    }

    // ─── Test 5: Legitimate Category Creation ────────────────────────────────

    @Test
    @DisplayName("Legitimate: Valid new category can be created and returns safe DTO")
    void testLegitimateCategoryCreation() throws Exception {
        when(categoryRepository.existsByNameIgnoreCase("Freelancing")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            return Category.builder()
                    .id(16)
                    .name(c.getName())
                    .type(c.getType())
                    .isEssential(c.getIsEssential())
                    .build();
        });

        String validPayload = """
            {
              "name": "Freelancing",
              "type": "INVESTMENT",
              "isEssential": true
            }
            """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(16))
                .andExpect(jsonPath("$.name").value("Freelancing"))
                .andExpect(jsonPath("$.type").value("INVESTMENT"))
                .andExpect(jsonPath("$.isEssential").value(true));

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("GET /api/categories returns CategoryDto list safely")
    void testGetAllCategories() throws Exception {
        when(categoryRepository.findAll()).thenReturn(List.of(
                Category.builder().id(1).name("Food").type("NEED").isEssential(true).build(),
                Category.builder().id(2).name("Transport").type("NEED").isEssential(true).build()
        ));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[1].name").value("Transport"));
    }

    @Test
    @DisplayName("Security: Null request is rejected with IllegalArgumentException")
    void testNullRequestRejected() {
        assertThatThrownBy(() -> categoryService.createCategory(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category request must not be null");
    }

    @Test
    @DisplayName("CategoryDto: fromEntity returns null when entity is null")
    void testCategoryDtoFromNullEntity() {
        assertThat(CategoryDto.fromEntity(null)).isNull();
    }
}

package com.Wealthify.backend.config;

import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the `categories` table on startup if it's empty.
 *
 * ExpenseService matches the AI's returned category string (e.g. "Transport")
 * against Category.name via equalsIgnoreCase. With no rows in the table,
 * that match always fails and every expense silently saves with a null
 * category - which the frontend then renders as "Uncategorized" regardless
 * of how confident or correct the AI's categorization actually was.
 *
 * These 15 names must stay in sync with the "Available categories" list in
 * AiService.buildPrompt().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CategorySeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("Categories table already seeded ({} rows) - skipping.",
                    categoryRepository.count());
            return;
        }

        List<Category> defaults = List.of(
                Category.builder().name("Food").type("NEED").isEssential(true).build(),
                Category.builder().name("Transport").type("NEED").isEssential(true).build(),
                Category.builder().name("Housing").type("NEED").isEssential(true).build(),
                Category.builder().name("Healthcare").type("NEED").isEssential(true).build(),
                Category.builder().name("Utilities").type("NEED").isEssential(true).build(),
                Category.builder().name("Entertainment").type("WANT").isEssential(false).build(),
                Category.builder().name("Shopping").type("WANT").isEssential(false).build(),
                Category.builder().name("Dining Out").type("WANT").isEssential(false).build(),
                Category.builder().name("Travel").type("WANT").isEssential(false).build(),
                Category.builder().name("Subscriptions").type("WANT").isEssential(false).build(),
                Category.builder().name("Investments").type("INVESTMENT").isEssential(true).build(),
                Category.builder().name("Savings").type("INVESTMENT").isEssential(true).build(),
                Category.builder().name("Education").type("NEED").isEssential(true).build(),
                Category.builder().name("Personal Care").type("NEED").isEssential(true).build(),
                Category.builder().name("Miscellaneous").type("WANT").isEssential(false).build()
        );

        categoryRepository.saveAll(defaults);
        log.info("Seeded {} default categories.", defaults.size());
    }
}
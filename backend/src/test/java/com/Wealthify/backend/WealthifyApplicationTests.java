package com.Wealthify.backend;

import com.Wealthify.backend.config.AbstractDatabaseIsolationTest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class WealthifyApplicationTests extends AbstractDatabaseIsolationTest {

	@Test
	void contextLoads() {
	}

}

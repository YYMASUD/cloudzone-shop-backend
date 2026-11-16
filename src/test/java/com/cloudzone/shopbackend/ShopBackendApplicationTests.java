package com.cloudzone.shopbackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that the application context starts.
 *
 * <p>Runs under the {@code schema} profile (H2 in PostgreSQL mode) so it does not
 * require Docker. To exercise real PostgreSQL + Redis via Testcontainers instead,
 * add {@code @Import(TestcontainersConfiguration.class)} and remove the profile —
 * that path needs a Docker-capable host.
 */
@ActiveProfiles("schema")
@SpringBootTest
class ShopBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}

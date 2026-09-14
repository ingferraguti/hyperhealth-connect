package io.hyperhealth.connect.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import io.hyperhealth.connect.controlplane.inventory.ScopedEndpointRepository;
import io.hyperhealth.connect.controlplane.inventory.ScopedInventoryService;
import io.hyperhealth.connect.controlplane.inventory.api.InventoryProblemAdvice;
import io.hyperhealth.connect.controlplane.inventory.api.ScopedEndpointController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ControlPlaneApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContextStartsWithInventoryFailClosedByDefault() {
        assertThat(applicationContext.getBeansOfType(ScopedInventoryService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ScopedEndpointRepository.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ScopedEndpointController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(InventoryProblemAdvice.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(HikariDataSource.class)).isEmpty();
    }
}

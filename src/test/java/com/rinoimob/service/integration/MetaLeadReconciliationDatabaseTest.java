package com.rinoimob.service.integration;

import com.rinoimob.domain.repository.IntegrationEventRepository;
import com.rinoimob.domain.repository.IntegrationRouteRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "META_SYNC_TEST_DB_URL", matches = ".+")
class MetaLeadReconciliationDatabaseTest {
    private DriverManagerDataSource base;
    private DriverManagerDataSource dataSource;
    private NamedParameterJdbcTemplate jdbc;
    private String schema;
    private final UUID tenant = UUID.randomUUID();
    private final UUID connection = UUID.randomUUID();
    private final UUID route = UUID.randomUUID();

    @BeforeEach
    void setup() {
        String url = System.getenv("META_SYNC_TEST_DB_URL");
        base = new DriverManagerDataSource(url, System.getenv("META_SYNC_TEST_DB_USER"), System.getenv("META_SYNC_TEST_DB_PASSWORD"));
        schema = "meta_sync_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(base).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                System.getenv("META_SYNC_TEST_DB_USER"), System.getenv("META_SYNC_TEST_DB_PASSWORD"));
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        var raw = new JdbcTemplate(dataSource);
        raw.execute("CREATE TABLE integration_connections(id UUID PRIMARY KEY, tenant_id UUID, provider VARCHAR(30), status VARCHAR(30))");
        raw.execute("CREATE TABLE integration_routes(id UUID PRIMARY KEY, tenant_id UUID, connection_id UUID, active BOOLEAN DEFAULT TRUE)");
        raw.execute("CREATE TABLE lead_acquisitions(id UUID PRIMARY KEY, tenant_id UUID, connection_id UUID, external_lead_id VARCHAR(200))");
        raw.execute("""
                CREATE TABLE integration_events(id UUID PRIMARY KEY, tenant_id UUID, connection_id UUID,
                provider VARCHAR(30), external_event_id VARCHAR(200), external_resource_id VARCHAR(160),
                event_type VARCHAR(80), status VARCHAR(30), encrypted_payload TEXT, attempt_count INT,
                next_attempt_at TIMESTAMP, received_at TIMESTAMP, updated_at TIMESTAMP,
                UNIQUE(connection_id, external_event_id))
                """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V68__meta_lead_reconciliation.sql")).execute(dataSource);
        jdbc.update("INSERT INTO integration_connections VALUES (:id,:tenant,'META_LEAD_ADS','ACTIVE')", Map.of("id", connection,"tenant",tenant));
        jdbc.update("INSERT INTO integration_routes(id,tenant_id,connection_id) VALUES (:id,:tenant,:connection)",
                Map.of("id",route,"tenant",tenant,"connection",connection));
    }

    @AfterEach
    void cleanup() {
        if (base != null && schema != null) new JdbcTemplate(base).execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private String query(Class<?> type, String method) {
        return Arrays.stream(type.getMethods()).filter(item -> item.getName().equals(method))
                .findFirst().orElseThrow().getAnnotation(Query.class).value();
    }

    private Map<String,Object> eventParameters(UUID selectedTenant, UUID selectedConnection) {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("id", UUID.randomUUID());
        parameters.put("tenantId", selectedTenant);
        parameters.put("connectionId", selectedConnection);
        parameters.put("provider", "META_LEAD_ADS");
        parameters.put("externalId", "lead-123");
        parameters.put("resourceId", "form-123");
        parameters.put("eventType", "LEADGEN_RECOVERED");
        parameters.put("status", "RECEIVED");
        parameters.put("payload", "encrypted");
        parameters.put("now", LocalDateTime.now());
        return parameters;
    }

    @Test
    void shouldAtomicallyDeduplicateConcurrentWebhookAndRecoveryWithoutPoisoningTransaction() throws Exception {
        String insert = query(IntegrationEventRepository.class,"insertIfAbsent");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> {
                start.await();
                return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
                    int inserted = jdbc.update(insert,eventParameters(tenant,connection));
                    assertThat(jdbc.getJdbcTemplate().queryForObject("SELECT 1",Integer.class)).isEqualTo(1);
                    return inserted;
                });
            };
            Future<Integer> first=executor.submit(task);
            Future<Integer> second=executor.submit(task);
            start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(1,0);
            assertThat(jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM integration_events",Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void shouldNotDuplicateAnAcquiredLeadAfterInboxRetentionOrCrossTenantBoundary() {
        String insert = query(IntegrationEventRepository.class,"insertIfAbsent");
        jdbc.update("INSERT INTO lead_acquisitions VALUES (:id,:tenant,:connection,'lead-123')",
                Map.of("id",UUID.randomUUID(),"tenant",tenant,"connection",connection));
        assertThat(jdbc.update(insert,eventParameters(tenant,connection))).isZero();
        assertThat(jdbc.update(insert,eventParameters(UUID.randomUUID(),connection))).isZero();
        UUID otherTenant=UUID.randomUUID();
        UUID otherConnection=UUID.randomUUID();
        jdbc.update("INSERT INTO integration_connections VALUES (:id,:tenant,'META_LEAD_ADS','ACTIVE')",Map.of("id",otherConnection,"tenant",otherTenant));
        assertThat(jdbc.update(insert,eventParameters(otherTenant,otherConnection))).isEqualTo(1);
    }

    @Test
    void shouldSkipLockedRoutesAndNeverLockAnotherTenantsRoute() throws Exception {
        String lock = query(IntegrationRouteRepository.class,"lockDueMetaSyncRoute");
        Map<String,Object> parameters = Map.of("tenantId",tenant,"id",route,"now",OffsetDateTime.now(ZoneOffset.UTC));
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch acquired=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        try {
            Future<?> worker=executor.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
                assertThat(jdbc.queryForList(lock,parameters)).hasSize(1);
                acquired.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Lock test timed out"); }
                catch (InterruptedException exception) { throw new IllegalStateException(exception); }
            }));
            assertThat(acquired.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForList(lock,parameters)).isEmpty();
            release.countDown();
            worker.get(10,TimeUnit.SECONDS);
            assertThat(jdbc.queryForList(lock,Map.of("tenantId",UUID.randomUUID(),"id",route,"now",OffsetDateTime.now()))).isEmpty();
            assertThat(jdbc.queryForList(lock,parameters)).hasSize(1);
        } finally { release.countDown();executor.shutdownNow(); }
    }
    @Test
    void shouldDiscoverOnlyActiveMetaRoutesWithMatchingTenant() {
        String discovery = query(IntegrationRouteRepository.class, "findDueMetaSyncTargets");
        var parameters = Map.of("now", OffsetDateTime.now());
        assertThat(jdbc.queryForList(discovery, parameters)).hasSize(1);
        jdbc.getJdbcTemplate().update("UPDATE integration_connections SET status='PAUSED'");
        assertThat(jdbc.queryForList(discovery, parameters)).isEmpty();
        jdbc.getJdbcTemplate().update("UPDATE integration_connections SET status='ACTIVE',provider='GRUPO_OLX'");
        assertThat(jdbc.queryForList(discovery, parameters)).isEmpty();
        jdbc.getJdbcTemplate().update("UPDATE integration_connections SET provider='META_LEAD_ADS'");
        jdbc.update("UPDATE integration_routes SET tenant_id=:tenant", Map.of("tenant", UUID.randomUUID()));
        assertThat(jdbc.queryForList(discovery, parameters)).isEmpty();
    }

    @Test
    void shouldRollbackRecoveredEventsAndCursorTogetherOnPageFailure() {
        String insert = query(IntegrationEventRepository.class, "insertIfAbsent");
        assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            jdbc.update(insert, eventParameters(tenant, connection));
            jdbc.getJdbcTemplate().update("UPDATE integration_routes SET meta_sync_cursor='next-page',meta_recovered_count=1");
            throw new IllegalStateException("Page could not be completed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM integration_events", Integer.class)).isZero();
        assertThat(jdbc.getJdbcTemplate().queryForObject("SELECT meta_sync_cursor FROM integration_routes", String.class)).isNull();
        assertThat(jdbc.update(insert, eventParameters(tenant, connection))).isEqualTo(1);
    }

}

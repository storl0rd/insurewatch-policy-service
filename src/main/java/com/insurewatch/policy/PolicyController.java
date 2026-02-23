package com.insurewatch.policy;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PolicyController {

    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    @Autowired
    private PolicyRepository policyRepository;

    private Tracer tracer;
    private LongCounter policyLookups;
    private LongCounter policyErrors;

    // Chaos state
    private final Map<String, Boolean> chaosState = new ConcurrentHashMap<>(Map.of(
        "service_crash", false,
        "high_latency",  false,
        "db_failure",    false,
        "memory_spike",  false,
        "cpu_spike",     false
    ));
    private final List<byte[]> memoryHog = new ArrayList<>();

    @PostConstruct
    public void init() {
        tracer = GlobalOpenTelemetry.getTracer("policy-service", "1.0.0");
        Meter meter = GlobalOpenTelemetry.getMeter("policy-service");
        policyLookups = meter.counterBuilder("policy.lookups.total")
            .setDescription("Total policy lookups").build();
        policyErrors = meter.counterBuilder("policy.errors.total")
            .setDescription("Total policy errors").build();

        seedSampleData();
    }

    private void applyChaos() throws InterruptedException {
        if (chaosState.getOrDefault("service_crash", false)) {
            throw new RuntimeException("Service unavailable (chaos: service_crash)");
        }
        if (chaosState.getOrDefault("high_latency", false)) {
            long delay = 3000 + (long)(Math.random() * 5000);
            log.warn("Chaos: injecting {}ms latency", delay);
            Thread.sleep(delay);
        }
        if (chaosState.getOrDefault("db_failure", false)) {
            throw new RuntimeException("Database connection failed (chaos: db_failure)");
        }
        if (chaosState.getOrDefault("memory_spike", false)) {
            log.warn("Chaos: memory spike - allocating 50MB");
            memoryHog.add(new byte[50 * 1024 * 1024]);
        }
        if (chaosState.getOrDefault("cpu_spike", false)) {
            log.warn("Chaos: CPU spike");
            long end = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < end) {
                Math.sqrt(Math.random() * 1000000);
            }
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "service", "policy-service",
            "chaos", chaosState,
            "timestamp", new Date().toString()
        ));
    }

    @GetMapping("/policy/{customerId}")
    public ResponseEntity<?> getPolicy(@PathVariable String customerId) {
        Span span = tracer.spanBuilder("get_policy").startSpan();
        try (Scope scope = span.makeCurrent()) {
            applyChaos();
            span.setAttribute("customer.id", customerId);
            log.info("Looking up policy for customer: {}", customerId);

            List<Policy> policies = policyRepository.findByCustomerId(customerId);
            if (policies.isEmpty()) {
                policyErrors.add(1, io.opentelemetry.api.common.Attributes.builder()
                    .put("reason", "not_found").build());
                return ResponseEntity.notFound().build();
            }

            policyLookups.add(1);
            span.setAttribute("policies.count", policies.size());
            return ResponseEntity.ok(policies);
        } catch (Exception e) {
            span.recordException(e);
            log.error("Error fetching policy for {}: {}", customerId, e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }

    @GetMapping("/policy/{customerId}/coverage")
    public ResponseEntity<?> getCoverage(@PathVariable String customerId) {
        Span span = tracer.spanBuilder("get_coverage").startSpan();
        try (Scope scope = span.makeCurrent()) {
            applyChaos();
            span.setAttribute("customer.id", customerId);
            log.info("Fetching coverage for customer: {}", customerId);

            Optional<Policy> activePolicy = policyRepository
                .findByCustomerIdAndStatus(customerId, "active");

            if (activePolicy.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Policy policy = activePolicy.get();
            span.setAttribute("policy.number", policy.getPolicyNumber());
            span.setAttribute("coverage.limit", policy.getCoverageLimit());

            Map<String, Object> response = new HashMap<>();
            response.put("customer_id", customerId);
            response.put("policy_number", policy.getPolicyNumber());
            response.put("policy_type", policy.getPolicyType());
            response.put("status", policy.getStatus());
            response.put("coverage_limit", policy.getCoverageLimit());
            response.put("coverages", policy.getCoverages());
            response.put("premium_amount", policy.getPremiumAmount());

            policyLookups.add(1);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            span.recordException(e);
            log.error("Coverage lookup error: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }

    @GetMapping("/chaos/state")
    public ResponseEntity<Map<String, Boolean>> getChaosState() {
        return ResponseEntity.ok(chaosState);
    }

    @PostMapping("/chaos/set")
    public ResponseEntity<?> setChaos(@RequestBody Map<String, Boolean> state) {
        state.forEach((key, value) -> {
            if (chaosState.containsKey(key)) {
                chaosState.put(key, value);
                log.warn("Chaos state updated: {}={}", key, value);
            }
        });
        if (!chaosState.getOrDefault("memory_spike", false)) {
            memoryHog.clear();
        }
        return ResponseEntity.ok(Map.of("status", "updated", "chaos", chaosState));
    }

    private void seedSampleData() {
        if (policyRepository.count() == 0) {
            log.info("Seeding sample policy data...");
            String[] customers = {"CUST001", "CUST002", "CUST003", "CUST004", "CUST005"};
            String[] types = {"health", "auto", "property", "life"};
            for (int i = 0; i < customers.length; i++) {
                Policy p = new Policy();
                p.setCustomerId(customers[i]);
                p.setPolicyNumber("POL-2024-" + String.format("%04d", i + 1));
                p.setPolicyType(types[i % types.length]);
                p.setStatus("active");
                p.setPremiumAmount(200 + (i * 50));
                p.setStartDate("2024-01-01");
                p.setEndDate("2025-12-31");
                p.setCoverages(List.of(
                    new Policy.Coverage("medical",   100000, 500,  2000),
                    new Policy.Coverage("emergency",  50000, 250,   500),
                    new Policy.Coverage("dental",      5000, 100,   300)
                ));
                policyRepository.save(p);
            }
            log.info("Seeded {} policies", customers.length);
        }
    }
}

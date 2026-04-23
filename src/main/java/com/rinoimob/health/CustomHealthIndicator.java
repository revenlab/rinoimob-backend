package com.rinoimob.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.MemoryMXBean;
import java.lang.management.ManagementFactory;

@Component
public class CustomHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long memoryUsage = memoryBean.getHeapMemoryUsage().getUsed();
        long memoryMax = memoryBean.getHeapMemoryUsage().getMax();
        double memoryPercentage = (memoryUsage / (double) memoryMax) * 100;

        if (memoryPercentage > 90) {
            return Health.down()
                    .withDetail("heap_memory_usage_mb", memoryUsage / (1024 * 1024))
                    .withDetail("heap_memory_max_mb", memoryMax / (1024 * 1024))
                    .withDetail("heap_memory_percentage", String.format("%.2f%%", memoryPercentage))
                    .build();
        } else if (memoryPercentage > 75) {
            return Health.outOfService()
                    .withDetail("heap_memory_usage_mb", memoryUsage / (1024 * 1024))
                    .withDetail("heap_memory_max_mb", memoryMax / (1024 * 1024))
                    .withDetail("heap_memory_percentage", String.format("%.2f%%", memoryPercentage))
                    .build();
        }

        return Health.up()
                .withDetail("heap_memory_usage_mb", memoryUsage / (1024 * 1024))
                .withDetail("heap_memory_max_mb", memoryMax / (1024 * 1024))
                .withDetail("heap_memory_percentage", String.format("%.2f%%", memoryPercentage))
                .build();
    }
}

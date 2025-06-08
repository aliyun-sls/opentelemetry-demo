package com.otel.demo.inventory.service;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 故障注入服务 - 用于混沌工程测试
 * 提供各种故障模拟功能，包括延迟、异常、资源耗尽等
 */
@Service
public class ChaosEngineeringService {

    private static final Logger logger = LoggerFactory.getLogger(ChaosEngineeringService.class);
    private final Client featureFlagClient;
    private final Random random = new Random();
    private final List<byte[]> memoryLeakList = new ArrayList<>();

    public ChaosEngineeringService() {
        this.featureFlagClient = OpenFeatureAPI.getInstance().getClient();
    }

    public void injectFailure(String operation) {
        boolean failureEnabled = featureFlagClient.getBooleanValue("inventoryServiceFailure", false);
        
        if (failureEnabled) {
            Span.current().setAttribute("chaos.failure_injection", true);
            Span.current().setAttribute("chaos.operation", operation);
            
            logger.warn("故障注入已启用 - 操作: {}", operation);
            throw new RuntimeException("故障注入: " + operation + " 操作失败");
        }
    }

    public void injectLatency(String operation) {
        int latencyMs = featureFlagClient.getIntegerValue("inventoryServiceLatency", 0);
        
        if (latencyMs > 0) {
            Span.current().setAttribute("chaos.latency_injection", true);
            Span.current().setAttribute("chaos.latency_ms", latencyMs);
            Span.current().setAttribute("chaos.operation", operation);
            
            logger.warn("延迟注入已启用 - 操作: {}, 延迟: {}ms", operation, latencyMs);
            
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("延迟注入被中断", e);
            }
        }
    }

    public void injectDatabaseFailure(String operation) {
        boolean dbFailureEnabled = featureFlagClient.getBooleanValue("inventoryDatabaseFailure", false);
        
        if (dbFailureEnabled) {
            Span.current().setAttribute("chaos.database_failure_injection", true);
            Span.current().setAttribute("chaos.operation", operation);
            
            logger.error("数据库故障注入已启用 - 操作: {}", operation);
            throw new RuntimeException("数据库连接失败: " + operation);
        }
    }

    public void injectSlowQuery(String operation) {
        boolean slowQueryEnabled = featureFlagClient.getBooleanValue("inventorySlowQuery", false);
        
        if (slowQueryEnabled) {
            Span.current().setAttribute("chaos.slow_query_injection", true);
            Span.current().setAttribute("chaos.operation", operation);
            
            logger.warn("慢查询注入已启用 - 操作: {}", operation);
            
            // 这个方法会被InventoryServiceImpl调用来执行真实的慢SQL
            // 具体的慢SQL执行逻辑在InventoryServiceImpl中实现
        }
    }

    /**
     * 检查是否需要执行慢SQL查询
     */
    public boolean shouldExecuteSlowQuery() {
        return featureFlagClient.getBooleanValue("inventorySlowQuery", false);
    }

    public void injectMemoryLeak(String operation) {
        boolean memoryLeakEnabled = featureFlagClient.getBooleanValue("inventoryMemoryLeak", false);
        
        if (memoryLeakEnabled) {
            // 每次调用分配1MB内存且不释放
            byte[] memoryChunk = new byte[1024 * 1024]; // 1MB
            memoryLeakList.add(memoryChunk);
            
            Span.current().setAttribute("chaos.memory_leak_injection", true);
            Span.current().setAttribute("chaos.memory_leak_size_mb", memoryLeakList.size());
            Span.current().setAttribute("chaos.operation", operation);
            
            logger.warn("内存泄漏注入已启用 - 操作: {}, 已分配内存: {}MB", 
                       operation, memoryLeakList.size());
        }
    }

    public void injectHighCpu(String operation) {
        boolean highCpuEnabled = featureFlagClient.getBooleanValue("inventoryHighCpu", false);
        
        if (highCpuEnabled) {
            Span.current().setAttribute("chaos.high_cpu_injection", true);
            Span.current().setAttribute("chaos.operation", operation);
            
            logger.warn("高CPU使用率注入已启用 - 操作: {}", operation);
            
            // 创建CPU密集型任务，持续1-3秒
            long endTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 3000);
            double result = 0;
            
            while (System.currentTimeMillis() < endTime) {
                result += Math.sqrt(ThreadLocalRandom.current().nextDouble());
            }
            
            // 防止编译器优化掉计算
            Span.current().setAttribute("chaos.cpu_calculation_result", result);
        }
    }

    /**
     * 综合故障注入方法 - 根据Flagd配置执行相应的故障注入
     */
    @WithSpan("chaos.inject_comprehensive")
    public void injectChaos(String operation) {
        try {
            // 按顺序执行各种故障注入
            injectLatency(operation);
            injectSlowQuery(operation);
            injectMemoryLeak(operation);
            injectHighCpu(operation);
            injectDatabaseFailure(operation);
            injectFailure(operation);
            
        } catch (Exception e) {
            Span.current().setAttribute("chaos.injection_error", e.getMessage());
            logger.error("故障注入过程中发生错误 - 操作: {}", operation, e);
            throw e;
        }
    }

    /**
     * 获取当前故障注入状态
     */
    @WithSpan("chaos.get_status")
    public ChaosStatus getChaosStatus() {
        return ChaosStatus.builder()
                .serviceFailureEnabled(featureFlagClient.getBooleanValue("inventoryServiceFailure", false))
                .latencyInjectionMs(featureFlagClient.getIntegerValue("inventoryServiceLatency", 0))
                .databaseFailureEnabled(featureFlagClient.getBooleanValue("inventoryDatabaseFailure", false))
                .slowQueryEnabled(featureFlagClient.getBooleanValue("inventorySlowQuery", false))
                .memoryLeakEnabled(featureFlagClient.getBooleanValue("inventoryMemoryLeak", false))
                .highCpuEnabled(featureFlagClient.getBooleanValue("inventoryHighCpu", false))
                .memoryLeakSizeMb(memoryLeakList.size())
                .build();
    }

    /**
     * 清理内存泄漏
     */
    @WithSpan("chaos.cleanup_memory_leak")
    public void cleanupMemoryLeak() {
        int previousSize = memoryLeakList.size();
        memoryLeakList.clear();
        System.gc(); // 建议垃圾回收
        
        logger.info("内存泄漏清理完成，释放了 {}MB 内存", previousSize);
        
        Span.current().setAttribute("chaos.memory_cleanup_mb", previousSize);
    }

    /**
     * 故障注入状态数据类
     */
    public static class ChaosStatus {
        private boolean serviceFailureEnabled;
        private int latencyInjectionMs;
        private boolean databaseFailureEnabled;
        private boolean slowQueryEnabled;
        private boolean memoryLeakEnabled;
        private boolean highCpuEnabled;
        private int memoryLeakSizeMb;

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public boolean isServiceFailureEnabled() { return serviceFailureEnabled; }
        public int getLatencyInjectionMs() { return latencyInjectionMs; }
        public boolean isDatabaseFailureEnabled() { return databaseFailureEnabled; }
        public boolean isSlowQueryEnabled() { return slowQueryEnabled; }
        public boolean isMemoryLeakEnabled() { return memoryLeakEnabled; }
        public boolean isHighCpuEnabled() { return highCpuEnabled; }
        public int getMemoryLeakSizeMb() { return memoryLeakSizeMb; }

        public static class Builder {
            private ChaosStatus status = new ChaosStatus();

            public Builder serviceFailureEnabled(boolean enabled) {
                status.serviceFailureEnabled = enabled;
                return this;
            }

            public Builder latencyInjectionMs(int ms) {
                status.latencyInjectionMs = ms;
                return this;
            }

            public Builder databaseFailureEnabled(boolean enabled) {
                status.databaseFailureEnabled = enabled;
                return this;
            }

            public Builder slowQueryEnabled(boolean enabled) {
                status.slowQueryEnabled = enabled;
                return this;
            }

            public Builder memoryLeakEnabled(boolean enabled) {
                status.memoryLeakEnabled = enabled;
                return this;
            }

            public Builder highCpuEnabled(boolean enabled) {
                status.highCpuEnabled = enabled;
                return this;
            }

            public Builder memoryLeakSizeMb(int sizeMb) {
                status.memoryLeakSizeMb = sizeMb;
                return this;
            }

            public ChaosStatus build() {
                return status;
            }
        }
    }
} 
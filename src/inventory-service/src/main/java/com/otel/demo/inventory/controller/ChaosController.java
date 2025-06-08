package com.otel.demo.inventory.controller;

import com.otel.demo.inventory.service.ChaosEngineeringService;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chaos")
public class ChaosController {

    private static final Logger logger = LoggerFactory.getLogger(ChaosController.class);

    @Autowired
    private ChaosEngineeringService chaosEngineeringService;

    /**
     * 获取故障注入状态
     */
    @GetMapping("/status")
    @WithSpan("chaos.get_status")
    public ResponseEntity<ChaosEngineeringService.ChaosStatus> getChaosStatus() {
        logger.info("获取故障注入状态");
        
        ChaosEngineeringService.ChaosStatus status = chaosEngineeringService.getChaosStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 手动触发故障注入测试
     */
    @PostMapping("/inject/{operation}")
    @WithSpan("chaos.manual_inject")
    public ResponseEntity<Map<String, Object>> manualInject(@PathVariable String operation) {
        logger.info("手动触发故障注入测试: {}", operation);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            chaosEngineeringService.injectChaos(operation);
            response.put("success", true);
            response.put("message", "故障注入执行成功");
            response.put("operation", operation);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "故障注入触发异常: " + e.getMessage());
            response.put("operation", operation);
            response.put("error", e.getClass().getSimpleName());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 手动触发延迟注入
     */
    @PostMapping("/inject/latency/{operation}")
    @WithSpan("chaos.inject_latency")
    public ResponseEntity<Map<String, Object>> injectLatency(@PathVariable String operation) {
        logger.info("手动触发延迟注入: {}", operation);
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            chaosEngineeringService.injectLatency(operation);
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("success", true);
            response.put("message", "延迟注入执行完成");
            response.put("operation", operation);
            response.put("duration_ms", duration);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "延迟注入失败: " + e.getMessage());
            response.put("operation", operation);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 手动触发数据库故障注入
     */
    @PostMapping("/inject/database/{operation}")
    @WithSpan("chaos.inject_database_failure")
    public ResponseEntity<Map<String, Object>> injectDatabaseFailure(@PathVariable String operation) {
        logger.info("手动触发数据库故障注入: {}", operation);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            chaosEngineeringService.injectDatabaseFailure(operation);
            response.put("success", true);
            response.put("message", "数据库故障注入执行成功");
            response.put("operation", operation);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "数据库故障注入触发异常: " + e.getMessage());
            response.put("operation", operation);
            response.put("error", e.getClass().getSimpleName());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 手动触发慢查询注入
     */
    @PostMapping("/inject/slow-query/{operation}")
    @WithSpan("chaos.inject_slow_query")
    public ResponseEntity<Map<String, Object>> injectSlowQuery(@PathVariable String operation) {
        logger.info("手动触发慢查询注入: {}", operation);
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            chaosEngineeringService.injectSlowQuery(operation);
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("success", true);
            response.put("message", "慢查询注入执行完成");
            response.put("operation", operation);
            response.put("duration_ms", duration);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "慢查询注入失败: " + e.getMessage());
            response.put("operation", operation);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 手动触发内存泄漏注入
     */
    @PostMapping("/inject/memory-leak/{operation}")
    @WithSpan("chaos.inject_memory_leak")
    public ResponseEntity<Map<String, Object>> injectMemoryLeak(@PathVariable String operation) {
        logger.info("手动触发内存泄漏注入: {}", operation);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            chaosEngineeringService.injectMemoryLeak(operation);
            response.put("success", true);
            response.put("message", "内存泄漏注入执行成功");
            response.put("operation", operation);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "内存泄漏注入失败: " + e.getMessage());
            response.put("operation", operation);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 手动触发高CPU使用率注入
     */
    @PostMapping("/inject/high-cpu/{operation}")
    @WithSpan("chaos.inject_high_cpu")
    public ResponseEntity<Map<String, Object>> injectHighCpu(@PathVariable String operation) {
        logger.info("手动触发高CPU使用率注入: {}", operation);
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            chaosEngineeringService.injectHighCpu(operation);
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("success", true);
            response.put("message", "高CPU使用率注入执行完成");
            response.put("operation", operation);
            response.put("duration_ms", duration);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "高CPU使用率注入失败: " + e.getMessage());
            response.put("operation", operation);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 清理内存泄漏
     */
    @PostMapping("/cleanup/memory-leak")
    @WithSpan("chaos.cleanup_memory_leak")
    public ResponseEntity<Map<String, Object>> cleanupMemoryLeak() {
        logger.info("清理内存泄漏");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            chaosEngineeringService.cleanupMemoryLeak();
            response.put("success", true);
            response.put("message", "内存泄漏清理完成");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "内存泄漏清理失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @WithSpan("chaos.health_check")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "chaos-engineering");
        health.put("timestamp", System.currentTimeMillis());
        
        ChaosEngineeringService.ChaosStatus chaosStatus = chaosEngineeringService.getChaosStatus();
        health.put("chaos_status", chaosStatus);
        
        return ResponseEntity.ok(health);
    }
} 
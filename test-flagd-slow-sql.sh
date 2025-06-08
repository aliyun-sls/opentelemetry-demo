#!/bin/bash

# 测试基于Flagd的真实慢SQL故障注入功能

set -e

INVENTORY_URL="http://localhost:9090"
FLAGD_UI_URL="http://localhost:8080"

echo "🧪 测试基于Flagd的真实慢SQL故障注入功能"
echo "========================================"

# 检查服务是否运行
echo "📋 检查服务状态..."
if ! curl -s "$INVENTORY_URL/api/v1/inventory/health" > /dev/null; then
    echo "❌ Inventory服务未运行，请先启动服务"
    exit 1
fi

echo "✅ Inventory服务运行正常"

# 1. 获取当前故障注入状态
echo ""
echo "📊 获取当前故障注入状态..."
curl -s "$INVENTORY_URL/api/v1/chaos/status" | jq '.' || echo "无法获取状态"

# 2. 测试正常查询性能（慢SQL关闭）
echo ""
echo "⚡ 测试正常查询性能（慢SQL关闭）..."
start_time=$(date +%s%3N)
curl -s "$INVENTORY_URL/api/v1/inventory/OLJCESPC7Z" > /dev/null
end_time=$(date +%s%3N)
normal_duration=$((end_time - start_time))
echo "正常查询耗时: ${normal_duration}ms"

# 3. 启用慢SQL注入
echo ""
echo "🐌 启用慢SQL注入..."
echo "请手动在Flagd UI中启用 'inventorySlowQuery' 标志"
echo "Flagd UI地址: $FLAGD_UI_URL"
echo "或者使用以下命令通过API启用:"
echo "curl -X PUT 'http://localhost:8013/flagd.evaluation.v1.Service/ResolveBoolean' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"flagKey\": \"inventorySlowQuery\", \"context\": {}}'"

read -p "按回车键继续测试（确保已启用慢SQL标志）..."

# 4. 测试慢SQL查询性能
echo ""
echo "🐌 测试慢SQL查询性能..."
echo "执行库存查询（应该触发复杂的SQL分析）..."

start_time=$(date +%s%3N)
response=$(curl -s "$INVENTORY_URL/api/v1/inventory/OLJCESPC7Z")
end_time=$(date +%s%3N)
slow_duration=$((end_time - start_time))

echo "慢SQL查询耗时: ${slow_duration}ms"
echo "性能差异: $((slow_duration - normal_duration))ms"

if [ $slow_duration -gt $((normal_duration + 1000)) ]; then
    echo "✅ 慢SQL注入生效！查询明显变慢"
else
    echo "⚠️  慢SQL注入可能未生效，或者查询仍然较快"
fi

# 5. 测试获取所有库存（应该触发趋势分析）
echo ""
echo "📈 测试库存趋势分析查询..."
start_time=$(date +%s%3N)
curl -s "$INVENTORY_URL/api/v1/inventory" > /dev/null
end_time=$(date +%s%3N)
trend_duration=$((end_time - start_time))
echo "趋势分析查询耗时: ${trend_duration}ms"

# 6. 测试低库存查询（应该触发深度分析）
echo ""
echo "🔍 测试库存深度分析查询..."
start_time=$(date +%s%3N)
curl -s "$INVENTORY_URL/api/v1/inventory/low-stock?threshold=50" > /dev/null
end_time=$(date +%s%3N)
deep_analysis_duration=$((end_time - start_time))
echo "深度分析查询耗时: ${deep_analysis_duration}ms"

# 7. 手动触发慢SQL测试
echo ""
echo "🔧 手动触发慢SQL测试..."
curl -s -X POST "$INVENTORY_URL/api/v1/chaos/inject/slow-query/manual-test" | jq '.'

# 8. 查看最终状态
echo ""
echo "📊 查看最终故障注入状态..."
curl -s "$INVENTORY_URL/api/v1/chaos/status" | jq '.'

# 9. 性能总结
echo ""
echo "📊 性能测试总结"
echo "=================="
echo "正常查询耗时:     ${normal_duration}ms"
echo "慢SQL查询耗时:    ${slow_duration}ms"
echo "趋势分析耗时:     ${trend_duration}ms"
echo "深度分析耗时:     ${deep_analysis_duration}ms"
echo ""

if [ $slow_duration -gt $((normal_duration * 2)) ]; then
    echo "✅ 慢SQL注入功能正常工作"
else
    echo "⚠️  慢SQL注入效果不明显，可能需要检查配置"
fi

echo ""
echo "💡 提示："
echo "1. 可以通过Flagd UI动态控制慢SQL注入: $FLAGD_UI_URL"
echo "2. 查看应用日志可以看到详细的慢SQL执行信息"
echo "3. 在OpenTelemetry追踪中可以看到慢SQL的执行时间和结果"
echo "4. 慢SQL查询包括："
echo "   - 复杂的库存统计分析"
echo "   - 递归CTE和窗口函数的趋势分析"
echo "   - 多表关联的深度分析"

echo ""
echo "🎯 测试完成！" 
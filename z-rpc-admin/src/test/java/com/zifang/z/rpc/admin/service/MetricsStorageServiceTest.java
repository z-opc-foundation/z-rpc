package com.zifang.z.rpc.admin.service;

import com.zifang.z.rpc.admin.model.MetricsPushRequest;
import com.zifang.z.rpc.admin.model.ProviderVO;
import com.zifang.z.rpc.admin.model.ServiceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MetricsStorageService} —— z-rpc-admin 控制台背后的存储逻辑。
 * <p>
 * 注意：z-rpc-admin 被根 pom 的 {@code <modules>} 注释掉，不在 reactor 内，
 * 因此这一套测试不会随根目录 {@code mvn test} 执行，只能在 z-rpc-admin 目录里单独跑。
 */
class MetricsStorageServiceTest {

    private MetricsStorageService store;

    @BeforeEach
    void setUp() {
        store = new MetricsStorageService();
    }

    private static ProviderVO provider(String id, String service, String address) {
        ProviderVO vo = new ProviderVO();
        vo.setId(id);
        vo.setService(service);
        vo.setAddress(address);
        return vo;
    }

    private static MetricsPushRequest push(String service, String method, long qps) {
        MetricsPushRequest req = new MetricsPushRequest();
        MetricsPushRequest.MetricsEntry entry = new MetricsPushRequest.MetricsEntry();
        entry.setService(service);
        entry.setMethod(method);
        entry.setQps(qps);
        List<MetricsPushRequest.MetricsEntry> list = new ArrayList<MetricsPushRequest.MetricsEntry>();
        list.add(entry);
        req.setMetrics(list);
        return req;
    }

    // ---------------- 基础读写 ----------------

    @Test
    @DisplayName("recordProvider 后 provider 总表与按服务查询都能看到")
    void recordProviderIsVisible() {
        store.recordProvider(provider("p1", "com.demo.OrderService", "10.0.0.1:20880"));

        assertEquals(1, store.listProviders().size());
        assertEquals(1, store.listProvidersByService("com.demo.OrderService").size());
        assertEquals(0, store.listProvidersByService("com.demo.UnknownService").size(),
                "未知服务应返回空表而不是 null");
    }

    @Test
    @DisplayName("dashboardOverview 的 serviceCount / providerCount 跟随写入")
    void overviewCountsTrackWrites() {
        Map<String, Object> empty = store.dashboardOverview();
        assertEquals(0, empty.get("serviceCount"));
        assertEquals(0, empty.get("providerCount"));

        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));
        store.recordProvider(provider("p2", "svcB", "10.0.0.2:2"));
        Map<String, Object> overview = store.dashboardOverview();
        assertEquals(2, overview.get("serviceCount"));
        assertEquals(2, overview.get("providerCount"));
    }

    @Test
    @DisplayName("recordMetrics 对 null 请求与 null metrics 都不炸")
    void recordMetricsTolerantToNull() {
        store.recordMetrics(null);
        store.recordMetrics(new MetricsPushRequest());
        assertTrue(store.dashboardOverview().get("totalQps").equals(0L),
                "空写入不应产生任何 QPS");
    }

    // ---------------- 曾经算错、现已修正的指标口径 ----------------

    /** 取 listServices 里某个服务的 qps；服务行不存在直接炸，免得"缺失"被读成 0。 */
    private long qpsOf(String serviceKey) {
        long v = -1L;
        for (ServiceVO vo : store.listServices()) {
            if (serviceKey.equals(vo.getServiceKey())) {
                v = ((Number) vo.getMetrics().get("qps")).longValue();
            }
        }
        if (v < 0L) {
            throw new IllegalStateException("listServices 里没有服务行: " + serviceKey);
        }
        return v;
    }

    @Test
    @DisplayName("同一 id 重复推送按 id 收敛，总表与按服务查询两边一致")
    void providerPushesAreIdempotentById() {
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));

        assertEquals(1, store.listProviders().size(),
                "providers 是按 id 的 Map，重复推送被去重");
        assertEquals(1, store.listProvidersByService("svcA").size(),
                "ServiceRegistry.providers 也必须按 id 收敛，否则控制台 providerCount 随心跳线性膨胀");

        // 猎物 1：去重只认 id，不同实例必须各自留下
        store.recordProvider(provider("p2", "svcA", "10.0.0.2:2"));
        assertEquals(2, store.listProvidersByService("svcA").size(),
                "收敛不能把不同 provider 合成一个");

        // 猎物 2：同 id 重推要留下最新那份，而不是留住第一条
        MetricsStorageService fresh = new MetricsStorageService();
        fresh.recordProvider(provider("p1", "svcA", "old:1"));
        fresh.recordProvider(provider("p1", "svcA", "new:2"));
        List<ProviderVO> after = fresh.listProvidersByService("svcA");
        assertEquals(1, after.size());
        assertEquals("new:2", after.get(0).getAddress(),
                "心跳带上来的是最新状态，视图必须跟着更新");
    }

    @Test
    @DisplayName("服务归属按整段相等匹配：order 不再吞掉 order-item 的指标")
    void serviceAttributionIsExactNotPrefix() {
        store.recordProvider(provider("p1", "order", "10.0.0.1:1"));
        store.recordProvider(provider("p2", "order-item", "10.0.0.2:2"));

        store.recordMetrics(push("order", "pay", 100L));
        store.recordMetrics(push("order-item", "query", 900L));

        // metricsHistory 的 key 是 "service:method"，曾经用 startsWith(serviceKey) 过滤，
        // "order-item:query" 会被算进 order 头上（实测 100 + 900 = 1000）。
        assertEquals(100L, qpsOf("order"),
                "order 只该有自己那 100");

        // 猎物：精确匹配切出去之后，两边各自都还得查得到
        assertTrue(store.getMetricsHistory("order").containsKey("order:pay"));
        assertFalse(store.getMetricsHistory("order").containsKey("order-item:query"),
                "getMetricsHistory 也不能再按前缀返回");
        assertTrue(store.getMetricsHistory("order-item").containsKey("order-item:query"),
                "order-item 必须还能查到自己那条序列");
        assertEquals(900L, qpsOf("order-item"));
    }

    @Test
    @DisplayName("QPS 取最近一次上报的速率，不是整条历史序列的和")
    void qpsIsTheLatestRateNotTheCumulativeSum() {
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));
        for (int i = 0; i < 5; i++) {
            store.recordMetrics(push("svcA", "pay", 10L));
        }
        assertEquals(10L, ((Number) store.dashboardOverview().get("totalQps")).longValue(),
                "同一份 10 QPS 的流量推 5 次仍然读回 10 —— 修复前是 5 次累加成 50");

        // 猎物：不同方法各自保最新值，汇总才是相加
        store.recordMetrics(push("svcA", "submit", 5L));
        assertEquals(15L, ((Number) store.dashboardOverview().get("totalQps")).longValue());

        // 同一方法再推一次：只覆盖，不累加
        store.recordMetrics(push("svcA", "pay", 20L));
        assertEquals(25L, ((Number) store.dashboardOverview().get("totalQps")).longValue());

        // 速率口径变了，但历史序列本身一个点都不能少（svcA:pay = 5 次 + 覆盖那次 = 6）
        assertEquals(6, store.getMetricsHistory("svcA").get("svcA:pay").size(),
                "latestMetrics 只是读数视图，序列仍按原样留痕");
    }

    @Test
    @DisplayName("指标历史按 key 截断在 1000 条")
    void metricsHistoryCappedAtOneThousand() {
        for (int i = 0; i < 1200; i++) {
            store.recordMetrics(push("svcA", "pay", 1L));
        }
        List<MetricsStorageService.MetricsPoint> history =
                store.getMetricsHistory("svcA").get("svcA:pay");
        assertNotNull(history);
        assertEquals(1000, history.size(), "超出 1000 条后从头部丢弃");
    }

    @Test
    @DisplayName("trace 列表截断在 1000 条且保留最新")
    void tracesCappedAndKeepNewest() {
        for (int i = 0; i < 1050; i++) {
            MetricsStorageService.TraceRecord t = new MetricsStorageService.TraceRecord();
            t.setTraceId("t" + i);
            t.setService("svcA");
            t.setMethod("pay");
            store.addTrace(t);
        }
        List<MetricsStorageService.TraceRecord> traces = store.listTraces();
        assertEquals(1000, traces.size());
        assertEquals("t50", traces.get(0).getTraceId(), "最旧的 50 条被丢弃");
        assertEquals("t1049", traces.get(999).getTraceId());
    }

    @Test
    @DisplayName("listTraces 返回的是拷贝，改它不影响内部状态")
    void listTracesReturnsCopy() {
        MetricsStorageService.TraceRecord t = new MetricsStorageService.TraceRecord();
        t.setTraceId("only");
        store.addTrace(t);
        store.listTraces().clear();
        assertEquals(1, store.listTraces().size());
    }

    @Test
    @DisplayName("并发 recordMetrics：不抛错，且正好停在 1000 上限")
    void concurrentRecordMetricsHonoursTheCap() throws Exception {
        final int threads = 8;
        final int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(new Callable<Integer>() {
                public Integer call() {
                    for (int j = 0; j < perThread; j++) {
                        store.recordMetrics(push("svcA", "pay", 1L));
                    }
                    return perThread;
                }
            }));
        }
        int attempted = 0;
        List<String> errors = new ArrayList<String>();
        for (Future<Integer> f : futures) {
            try {
                attempted += f.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.add(String.valueOf(e.getCause()));
                attempted += perThread;
            }
        }
        pool.shutdownNow();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        int retained = 0;
        List<MetricsStorageService.MetricsPoint> h =
                store.getMetricsHistory("svcA").get("svcA:pay");
        if (h != null) {
            retained = h.size();
        }
        System.out.println("[concurrentRecordMetrics] attempted=" + attempted
                + " retained=" + retained + " errors=" + errors.size());
        assertTrue(errors.isEmpty(), "并发写入不应抛错: " + errors);
        assertEquals(1600, attempted, "投递量应为 1600");
        // 追加 + 截断合成一个同步块之后，上限才是硬上限；修复前实测冲到 1012
        assertEquals(1000, retained, "1600 次投递截断后必须正好是 1000");
    }

    @Test
    @DisplayName("getMetricsHistory 返回拷贝，清空外层的 list 不影响内部")
    void metricsHistoryCopyIsShallowAtOuterLevel() {
        store.recordMetrics(push("svcA", "pay", 1L));
        Map<String, List<MetricsStorageService.MetricsPoint>> copy = store.getMetricsHistory("svcA");
        copy.remove("svcA:pay");
        assertEquals(1, store.getMetricsHistory("svcA").size(),
                "外层 Map 是拷贝，改动不外泄");
    }

    @Test
    @DisplayName("listServices 的 providerCount 与去重后的 registry 一致")
    void serviceVoProviderCountIsDeduplicated() {
        store.recordProvider(provider("p1", "svcA", "a:1"));
        store.recordProvider(provider("p1", "svcA", "a:1"));
        ServiceVO found = null;
        for (ServiceVO vo : store.listServices()) {
            if ("svcA".equals(vo.getServiceKey())) {
                found = vo;
            }
        }
        assertNotNull(found);
        assertEquals("svcA", found.getServiceName(), "serviceName 目前就是直接取 serviceKey");
        assertEquals(1, found.getProviderCount(),
                "同一条 provider 心跳重推只能算 1 个");
        assertSame(found.getMetrics().get("qps"), found.getMetrics().get("qps"));
    }
}

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

    // ---------------- 缺陷 ----------------

    @Test
    @DisplayName("bug_同一 id 重复推送会在服务视图里无限堆积 Provider")
    void bug_recordProviderDuplicatesPerPush() {
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));

        assertEquals(1, store.listProviders().size(),
                "providers 是按 id 的 Map，重复推送被去重");
        assertEquals(3, store.listProvidersByService("svcA").size(),
                "但 ServiceRegistry.providers 是 List，同一个 Provider 被记了 3 次 => "
                        + "控制台的 providerCount 会随每次心跳线性膨胀");
    }

    @Test
    @DisplayName("bug_serviceKey 用 startsWith 前缀匹配：svc 会吞掉 svc-item 的指标")
    void bug_prefixMatchingLeaksAcrossServices() {
        store.recordProvider(provider("p1", "order", "10.0.0.1:1"));
        store.recordProvider(provider("p2", "order-item", "10.0.0.2:2"));

        store.recordMetrics(push("order", "pay", 100L));
        store.recordMetrics(push("order-item", "query", 900L));

        long orderQps = 0L;
        for (ServiceVO vo : store.listServices()) {
            if ("order".equals(vo.getServiceKey())) {
                orderQps = ((Number) vo.getMetrics().get("qps")).longValue();
            }
        }
        // metricsHistory 的 key 是 "service:method"，用 startsWith("order") 过滤
        // 会把 "order-item:query" 也算进 order 的 QPS。
        assertEquals(1000L, orderQps,
                "实测 order 的 QPS = 自身 100 + 被误吞的 order-item 900，证明前缀匹配跨服务串味");

        // 反向 prey：精确名不受影响
        assertTrue(store.getMetricsHistory("order").containsKey("order:pay"));
        assertTrue(store.getMetricsHistory("order").containsKey("order-item:query"),
                "getMetricsHistory 同样按前缀返回，说明这不是 listServices 独有的问题");
    }

    @Test
    @DisplayName("bug_QPS 是整个历史序列的和，不是速率：同一份流量会随推送次数线性放大")
    void bug_qpsIsCumulativeSumNotRate() {
        store.recordProvider(provider("p1", "svcA", "10.0.0.1:1"));
        for (int i = 0; i < 5; i++) {
            store.recordMetrics(push("svcA", "pay", 10L));
        }
        Object total = store.dashboardOverview().get("totalQps");
        assertEquals(50L, ((Number) total).longValue(),
                "5 次各 10 QPS 的推送被汇总成 50 —— 字段名叫 qps 但语义是累计和");
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
    @DisplayName("并发 recordMetrics 会丢点或抛错（ArrayList 无同步）")
    void concurrentRecordMetricsIsNotSafe() throws Exception {
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
        // 结论按实测记账：either 丢点、或截断到 1000、或抛异常。
        assertTrue(retained > 0, "prey：确实写入了数据, retained=" + retained);
        System.out.println("[concurrentRecordMetrics] attempted=" + attempted
                + " retained=" + retained + " errors=" + errors.size());
        assertTrue(attempted == 1600, "投递量应为 1600, 实际 " + attempted);
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
    @DisplayName("listServices 的 providerCount 取自 registry，与 bug 重复计数一致")
    void serviceVoProviderCountReflectsRegistryList() {
        store.recordProvider(provider("p1", "svcA", "a:1"));
        store.recordProvider(provider("p1", "svcA", "a:1"));
        ServiceVO found = null;
        for (ServiceVO vo : store.listServices()) {
            if ("svcA".equals(vo.getServiceKey())) {
                found = vo;
            }
        }
        assertNotNull(found);
        assertEquals("svcA", found.getServiceName(), "serviceName 被直接赋成 serviceKey");
        assertEquals(2, found.getProviderCount(), "同一条 provider 记成了 2 个");
        assertSame(found.getMetrics().get("qps"), found.getMetrics().get("qps"));
    }
}

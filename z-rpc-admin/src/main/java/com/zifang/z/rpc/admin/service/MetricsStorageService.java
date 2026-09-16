package com.zifang.z.rpc.admin.service;

import com.zifang.z.rpc.admin.model.MetricsPushRequest;
import com.zifang.z.rpc.admin.model.ProviderVO;
import com.zifang.z.rpc.admin.model.ServiceVO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易内存存储（生产中应使用 z-config 持久化或专用 TSDB）
 */
@Service
public class MetricsStorageService {

    private static final Logger log = LogManager.getLogger(MetricsStorageService.class);

    /** 接收到的 Provider 实例 */
    private final Map<String, ProviderVO> providers = new ConcurrentHashMap<>();

    /** 服务注册表：serviceKey -> {providers, consumers} */
    private final Map<String, ServiceRegistry> services = new ConcurrentHashMap<>();

    /** 指标时序：service:method -> 时间序列 */
    private final Map<String, List<MetricsPoint>> metricsHistory = new ConcurrentHashMap<>();

    /** 链路追踪 */
    private final List<TraceRecord> traces = Collections.synchronizedList(new ArrayList<>());

    public void recordProvider(ProviderVO provider) {
        providers.put(provider.getId(), provider);
        services.computeIfAbsent(provider.getService(), k -> new ServiceRegistry())
                .providers.add(provider);
        log.info("Provider registered: {} -> {}", provider.getId(), provider.getAddress());
    }

    public void recordMetrics(MetricsPushRequest request) {
        if (request == null || request.getMetrics() == null) return;
        long now = System.currentTimeMillis();
        for (MetricsPushRequest.MetricsEntry e : request.getMetrics()) {
            String key = e.getService() + ":" + e.getMethod();
            metricsHistory.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new MetricsPoint(now, e.getQps(), e.getP50(), e.getP90(), e.getP99(), e.getErrorRate()));
            // 限制历史长度
            List<MetricsPoint> history = metricsHistory.get(key);
            if (history.size() > 1000) {
                history.remove(0);
            }
        }
    }

    public List<ServiceVO> listServices() {
        List<ServiceVO> result = new ArrayList<>();
        services.forEach((serviceKey, registry) -> {
            ServiceVO vo = new ServiceVO();
            vo.setServiceKey(serviceKey);
            vo.setServiceName(serviceKey);
            int p = vo.getProviderCount();
            vo.setProviderCount(registry.providers.size());
            vo.setConsumerCount(registry.consumers.size());
            // 计算 QPS
            long totalQps = metricsHistory.entrySet().stream()
                    .filter(en -> en.getKey().startsWith(serviceKey))
                    .flatMap(en -> en.getValue().stream())
                    .mapToLong(MetricsPoint::getQps)
                    .sum();
            Map<String, Object> m = new HashMap<>();
            m.put("qps", totalQps);
            vo.setMetrics(m);
            result.add(vo);
        });
        return result;
    }

    public List<ProviderVO> listProviders() {
        return new ArrayList<>(providers.values());
    }

    public List<ProviderVO> listProvidersByService(String serviceKey) {
        ServiceRegistry reg = services.get(serviceKey);
        if (reg == null) return new ArrayList<>();
        return new ArrayList<>(reg.providers);
    }

    public Map<String, List<MetricsPoint>> getMetricsHistory(String serviceKey) {
        Map<String, List<MetricsPoint>> result = new HashMap<>();
        metricsHistory.forEach((key, value) -> {
            if (key.startsWith(serviceKey)) {
                result.put(key, new ArrayList<>(value));
            }
        });
        return result;
    }

    public void addTrace(TraceRecord trace) {
        traces.add(trace);
        if (traces.size() > 1000) {
            traces.remove(0);
        }
    }

    public List<TraceRecord> listTraces() {
        return new ArrayList<>(traces);
    }

    public Map<String, Object> dashboardOverview() {
        Map<String, Object> overview = new HashMap<>();
        overview.put("serviceCount", services.size());
        overview.put("providerCount", providers.size());
        long totalQps = metricsHistory.values().stream()
                .flatMap(List::stream)
                .mapToLong(MetricsPoint::getQps)
                .sum();
        overview.put("totalQps", totalQps);
        return overview;
    }

    public static class ServiceRegistry {
        public final List<ProviderVO> providers = new ArrayList<>();
        public final List<ConsumerVO> consumers = new ArrayList<>();
    }

    public static class ConsumerVO {
        public String id;
        public String service;
        public String address;
    }

    public static class MetricsPoint {
        public long timestamp;
        public long qps;
        public long p50;
        public long p90;
        public long p99;
        public double errorRate;

        public MetricsPoint(long timestamp, long qps, long p50, long p90, long p99, double errorRate) {
            this.timestamp = timestamp;
            this.qps = qps;
            this.p50 = p50;
            this.p90 = p90;
            this.p99 = p99;
            this.errorRate = errorRate;
        }

        public long getTimestamp() { return timestamp; }
        public long getQps() { return qps; }
        public long getP50() { return p50; }
        public long getP90() { return p90; }
        public long getP99() { return p99; }
        public double getErrorRate() { return errorRate; }
    }

    public static class TraceRecord {
        public String traceId;
        public String service;
        public String method;
        public long startTime;
        public long rt;
        public boolean success;
        public String error;
        public String remoteAddress;

        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getRt() { return rt; }
        public void setRt(long rt) { this.rt = rt; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getRemoteAddress() { return remoteAddress; }
        public void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }
    }
}

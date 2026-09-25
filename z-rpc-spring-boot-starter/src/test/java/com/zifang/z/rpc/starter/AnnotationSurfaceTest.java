package com.zifang.z.rpc.starter;

import com.zifang.z.rpc.annotation.ZRpcReference;
import com.zifang.z.rpc.annotation.ZRpcService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code @ZRpcService} / {@code @ZRpcReference} 的注解契约。
 * 注解默认值是用户唯一能读到的文档，所以逐个钉死。
 */
class AnnotationSurfaceTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> defaultsOf(Class<? extends Annotation> ann) throws Exception {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Method[] methods = ann.getDeclaredMethods();
        for (Method m : methods) {
            Object v = m.getDefaultValue();
            if (v instanceof Class) {
                v = ((Class<?>) v).getName();
            } else if (v != null && v.getClass().isArray()) {
                if (((Object[]) v).length == 0) {
                    v = "[]";
                } else if (((Object[]) v)[0] instanceof Class) {
                    Class<?>[] cs = (Class<?>[]) v;
                    String[] names = new String[cs.length];
                    for (int i = 0; i < cs.length; i++) {
                        names[i] = cs[i].getName();
                    }
                    v = Arrays2.toString(names);
                } else {
                    v = Arrays2.toString((Object[]) v);
                }
            }
            out.put(m.getName(), v);
        }
        return out;
    }

    private static final class Arrays2 {
        static String toString(Object[] a) {
            StringBuilder sb = new StringBuilder();
            for (Object o : a) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(o);
            }
            return sb.toString();
        }
    }

    // ---------------- @ZRpcService ----------------

    @Test
    @DisplayName("@ZRpcService 的 retention 是 RUNTIME，target 只有 TYPE")
    void serviceAnnotationMeta() {
        Retention r = ZRpcService.class.getAnnotation(Retention.class);
        assertNotNull(r);
        assertEquals(RetentionPolicy.RUNTIME, r.value());
        Target t = ZRpcService.class.getAnnotation(Target.class);
        assertNotNull(t);
        assertArrayEquals(new ElementType[]{ElementType.TYPE}, t.value());
    }

    @Test
    @DisplayName("@ZRpcService 是 stereotype（元标注 @Component），但仍未 @Inherited")
    void zRpcServiceIsAComponentStereotype() {
        // 导出器是 BeanPostProcessor，只对已在容器里的 Bean 生效；
        // 不元标注 @Component 的话，光写 @ZRpcService 的类根本不会成为 Bean。
        assertNotNull(AnnotatedElementUtils.findMergedAnnotation(ZRpcService.class, Component.class),
                "@ZRpcService 必须被 @Component 元标注，否则 javadoc 的\"标记一个类为 RPC 服务实现\"不成立");
        assertNotNull(ZRpcService.class.getAnnotation(Component.class),
                "prey：@Component 是直接元标注在注解类型上的，不是继承来的");
        assertNull(ZRpcService.class.getAnnotation(Inherited.class),
                "仍未标 @Inherited：代理场景靠 getUserClass 解决（见 ZRpcServiceExporterTest），"
                        + "但父类注解被子类继承这一路依旧不通");
    }

    @Test
    @DisplayName("@ZRpcService 全部属性默认值")
    void serviceAnnotationDefaults() throws Exception {
        Map<String, Object> d = defaultsOf(ZRpcService.class);
        assertEquals(11, d.size(), "@ZRpcService 属性集合: " + d.keySet());
        assertEquals("void", d.get("interfaceClass"), "默认值是 void.class");
        assertEquals("", d.get("interfaceName"));
        assertEquals("1.0.0", d.get("version"));
        assertEquals("", d.get("group"));
        assertEquals(100, d.get("weight"));
        assertEquals(0, d.get("delay"));
        assertEquals(3000, d.get("timeout"));
        assertEquals(2, d.get("retries"));
        assertEquals("random", d.get("loadbalance"));
        assertEquals("failover", d.get("cluster"));
        assertEquals(false, d.get("async"));
    }

    // ---------------- @ZRpcReference ----------------

    @Test
    @DisplayName("@ZRpcReference 的 target 含 FIELD/METHOD/ANNOTATION_TYPE")
    void referenceAnnotationMeta() {
        Target t = ZRpcReference.class.getAnnotation(Target.class);
        assertNotNull(t);
        assertArrayEquals(
                new ElementType[]{ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE},
                t.value());
        assertEquals(RetentionPolicy.RUNTIME,
                ZRpcReference.class.getAnnotation(Retention.class).value());
    }

    @Test
    @DisplayName("@ZRpcReference 不得元标注 @Autowired：否则 Spring 会按类型抢注同一字段")
    void zRpcReferenceIsNotMetaAnnotatedAutowired() {
        assertNull(ZRpcReference.class.getAnnotation(Autowired.class),
                "@ZRpcReference 上的 @Autowired 已摘除：字段的代理只能由 ZRpcReferenceInjector 按属性构造");
        // 猎物：注解本身仍然是 RUNTIME 可见、仍带全部属性，否则"取不到 @Autowired"是空跑
        assertEquals(RetentionPolicy.RUNTIME, ZRpcReference.class.getAnnotation(Retention.class).value());
        assertEquals(17, ZRpcReference.class.getDeclaredMethods().length,
                "属性面仍在: " + ZRpcReference.class.getDeclaredMethods().length + " 个");
    }

    @Test
    @DisplayName("@ZRpcReference 全部属性默认值")
    void referenceAnnotationDefaults() throws Exception {
        Map<String, Object> d = defaultsOf(ZRpcReference.class);
        assertEquals(17, d.size(), "@ZRpcReference 属性集合: " + d.keySet());
        assertEquals("void", d.get("interfaceClass"), "默认值是 void.class");
        assertEquals("", d.get("interfaceName"));
        assertEquals("1.0.0", d.get("version"));
        assertEquals("", d.get("group"));
        assertEquals(3000, d.get("timeout"));
        assertEquals(2, d.get("retries"));
        assertEquals("random", d.get("loadbalance"));
        assertEquals("failover", d.get("cluster"));
        assertEquals(false, d.get("async"));
        assertEquals(false, d.get("oneway"));
        assertEquals("127.0.0.1:8084", d.get("registry"));
        assertEquals("", d.get("url"));
        assertTrue((Boolean) d.get("check"), "check 默认 true");
        assertFalse((Boolean) d.get("lazy"));
        assertEquals(0, d.get("connections"));
        assertEquals("netty", d.get("client"));
        assertEquals("hessian2", d.get("serialization"));
    }

    @Test
    @DisplayName("bug_check默认true_但无人实现：注入失败只 log.error，字段留 null")
    void bug_check_default_true_is_never_enforced() throws Exception {
        Map<String, Object> d = defaultsOf(ZRpcReference.class);
        assertEquals(true, d.get("check"));
        // check=true 的语义应当是"启动即校验 Provider 存在，不存在就失败"。
        // ReferenceConfig 上根本没有 setCheck —— 见 ZRpcReferenceInjectorTest 里的机械证明，
        // 所以该属性连落点都没有，纯粹是装饰。
        Set<String> setters = new java.util.TreeSet<String>();
        for (Method m : com.zifang.z.rpc.config.ReferenceConfig.class.getDeclaredMethods()) {
            if (m.getName().startsWith("set")) {
                setters.add(m.getName());
            }
        }
        assertFalse(setters.contains("setCheck"),
                "ReferenceConfig 没有 setCheck => @ZRpcReference.check 无落点: " + setters);
        assertFalse(setters.contains("setLazy"), "同理 lazy 无落点");
        assertFalse(setters.contains("setConnections"), "connections 无落点");
        assertFalse(setters.contains("setClient"), "client 无落点");
        assertFalse(setters.contains("setSerialization"), "serialization 无落点");
        // async/oneway 有 setter，但注入器不调用 —— 见 injector 测试
        assertTrue(setters.contains("setAsync") && setters.contains("setOneway"),
                "async/oneway 有 setter 却被注入器忽略: " + setters);
    }

    @Test
    @DisplayName("loadbalance 文档写了 consistenthash / cluster 写了 forking，但框架里没有这两个实现")
    void advertisedButMissingStrategies() {
        // 注解 javadoc 里列出的策略名必须能在 SPI 元文件里找到，否则用户配了就是随机/失败。
        Set<String> lbNames = SpiMetaReader.names(
                "META-INF/z-rpc/com.zifang.z.rpc.loadbalance.LoadBalance");
        Set<String> clusterNames = SpiMetaReader.names(
                "META-INF/z-rpc/com.zifang.z.rpc.cluster.Cluster");
        assertTrue(lbNames.contains("random") && lbNames.contains("roundrobin")
                        && lbNames.contains("leastactive"),
                "已实现的 LB: " + lbNames);
        assertFalse(lbNames.contains("consistenthash"),
                "@ZRpcReference javadoc 宣传 consistenthash，但 SPI 里没有 => 配了会静默退化");
        assertFalse(clusterNames.contains("forking"),
                "@ZRpcReference javadoc 宣传 forking，但 SPI 里没有 => 配了会静默退化");
    }

    /** 读 SPI 元文件的 name=class 列表。 */
    static final class SpiMetaReader {
        static Set<String> names(String resource) {
            Set<String> out = new java.util.LinkedHashSet<String>();
            java.io.InputStream in = SpiMetaReader.class.getClassLoader()
                    .getResourceAsStream(resource);
            if (in == null) {
                return out;
            }
            try {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(in, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) {
                        continue;
                    }
                    int eq = s.indexOf('=');
                    if (eq > 0) {
                        out.add(s.substring(0, eq).trim());
                    }
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            } finally {
                try {
                    in.close();
                } catch (java.io.IOException ignored) {
                    // 只读资源，关不上不影响结论
                }
            }
            return out;
        }
    }
}

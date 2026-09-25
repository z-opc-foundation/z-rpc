package com.zifang.z.rpc.starter;

import com.zifang.z.rpc.starter.annotation.EnableZRpc;
import com.zifang.z.rpc.starter.config.ZRpcAutoConfiguration;
import com.zifang.z.rpc.starter.config.ZRpcConsumerAutoConfiguration;
import com.zifang.z.rpc.starter.config.ZRpcFrameworkInitializer;
import com.zifang.z.rpc.starter.config.ZRpcRegistryAutoConfiguration;
import com.zifang.z.rpc.starter.config.ZRpcServerAutoConfiguration;
import com.zifang.z.rpc.starter.config.ZRpcServiceExporter;
import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动装配注册元数据的一致性检查。
 * <p>
 * 这一组测试刻意不启动 Spring 容器，只读资源文件本身：
 * starter 的价值全在"被 Boot 发现"，所以发现链路必须钉住。
 */
class AutoConfigurationMetadataTest {

    private static List<String> readClasspathLines(String path) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        List<String> lines = new ArrayList<String>();
        if (!res.exists()) {
            return lines;
        }
        InputStream in = res.getInputStream();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
        } finally {
            in.close();
        }
        return lines;
    }

    @Test
    @DisplayName("AutoConfiguration.imports 必须列出 ZRpcAutoConfiguration")
    void autoConfigurationImportsFile() throws Exception {
        List<String> lines = readClasspathLines(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertFalse(lines.isEmpty(), "AutoConfiguration.imports 为空 => Boot 2.7+ 发现不了自动装配");
        assertTrue(lines.contains(ZRpcAutoConfiguration.class.getName()),
                "imports 文件内容: " + lines);
    }

    @Test
    @DisplayName("spring.factories 必须用 EnableAutoConfiguration= 作为 key（老 Boot 只认这份）")
    void springFactoriesCarriesTheAutoConfigurationKey() throws Exception {
        List<String> lines = readClasspathLines("META-INF/spring.factories");
        assertFalse(lines.isEmpty(), "spring.factories 应当存在（文件在，才有脸谈格式）");

        boolean hasAutoConfigKey = false;
        for (String line : lines) {
            if (line.startsWith("org.springframework.boot.autoconfigure.EnableAutoConfiguration")) {
                hasAutoConfigKey = true;
            }
        }
        // Spring Boot 2.x 的 SpringFactoriesLoader 按 properties 解析：
        // 裸类名没有 key，会被整行丢弃 => 只认 spring.factories 的老版本 Boot 发现不了自动装配。
        assertTrue(hasAutoConfigKey,
                "spring.factories 内容 = " + lines + "；缺少 EnableAutoConfiguration= 前缀");
        assertTrue(lines.contains(ZRpcAutoConfiguration.class.getName())
                        || lines.contains(ZRpcAutoConfiguration.class.getName() + "\\"),
                "key 的价值里必须真的挂着装配类: " + lines);
    }

    @Test
    @DisplayName("@EnableZRpc 必须 @Import 装配类，否则它是空壳标记")
    void enableZRpcImportsTheAutoConfiguration() {
        Import imp = EnableZRpc.class.getAnnotation(Import.class);
        assertNotNull(imp,
                "@EnableZRpc 的 javadoc 声称\"激活 Z-RPC 自动装配\"，类上必须有 @Import/@ImportSelector/@ImportBeanDefinitionRegistrar");
        Set<Class<?>> imported = new LinkedHashSet<Class<?>>(Arrays.asList(imp.value()));
        assertTrue(imported.contains(ZRpcAutoConfiguration.class),
                "@EnableZRpc 应导入 ZRpcAutoConfiguration: " + imported);
        assertTrue(imported.contains(com.zifang.z.rpc.starter.config.ZRpcReferenceInjector.class),
                "prey：@EnableZRpc 还应带上 ReferenceInjector，让 @ZRpcReference 有处理者: " + imported);
    }

    @Test
    @DisplayName("EnableZRpc 只声明 scanBasePackages，且框架内没有任何代码读它")
    void enableZRpcAttributeSurface() {
        assertEquals(1, EnableZRpc.class.getDeclaredMethods().length,
                "EnableZRpc 属性: " + Arrays.toString(EnableZRpc.class.getDeclaredMethods()));
        assertEquals("scanBasePackages", EnableZRpc.class.getDeclaredMethods()[0].getName(),
                "javadoc 承诺扫描 @ZRpcService/@ZRpcReference，但该属性无人消费");
    }

    @Test
    @DisplayName("ZRpcAutoConfiguration 上的 @Import 覆盖三个子装配 + Exporter")
    void autoConfigurationImportsSiblings() {
        Import imp = ZRpcAutoConfiguration.class.getAnnotation(Import.class);
        assertNotNull(imp);
        Set<Class<?>> imported = new LinkedHashSet<Class<?>>(Arrays.asList(imp.value()));
        assertTrue(imported.contains(ZRpcServerAutoConfiguration.class), "缺 Server 装配: " + imported);
        assertTrue(imported.contains(ZRpcConsumerAutoConfiguration.class), "缺 Consumer 装配: " + imported);
        assertTrue(imported.contains(ZRpcRegistryAutoConfiguration.class), "缺 Registry 装配: " + imported);
        assertTrue(imported.contains(ZRpcServiceExporter.class), "缺 ServiceExporter: " + imported);
        assertTrue(imported.contains(com.zifang.z.rpc.starter.config.ZRpcReferenceInjector.class),
                "ReferenceInjector 必须被 @Import，否则 @ZRpcReference 永远不被注入: " + imported);
    }

    @Test
    @DisplayName("z.rpc.enabled=false 应关闭整套自动装配（matchIfMissing=true）")
    void conditionalOnPropertyContract() {
        ConditionalOnProperty cond =
                ZRpcAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(cond);
        assertEquals("z.rpc", cond.prefix());
        assertEquals(1, cond.name().length);
        assertEquals("enabled", cond.name()[0]);
        assertTrue(cond.matchIfMissing(), "缺省必须开启，否则 starter 用起来要显式配 enabled=true");
    }

    @Test
    @DisplayName("Server/Registry 子装配上没有任何 @ConditionalOnProperty —— server.enabled=false 只是运行期判断")
    void siblingConfigsAreUnconditional() {
        assertNullish(ZRpcServerAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class));
        assertNullish(ZRpcRegistryAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class));
        // 结论：RpcServer bean 总会被创建（会占端口），enabled=false 只阻止 start()。
        assertNotNull(new ZRpcProperties().getServer());
    }

    private static void assertNullish(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    @Test
    @DisplayName("ZRpcFrameworkInitializer 只有日志字段、零方法 => 纯打印构造器，无副作用")
    void frameworkInitializerIsLogOnly() {
        assertEquals(0, ZRpcFrameworkInitializer.class.getDeclaredMethods().length,
                "Initializer 不应有任何可调用行为: "
                        + Arrays.toString(ZRpcFrameworkInitializer.class.getDeclaredMethods()));
        for (java.lang.reflect.Field f : ZRpcFrameworkInitializer.class.getDeclaredFields()) {
            assertTrue(java.lang.reflect.Modifier.isStatic(f.getModifiers()),
                    "Initializer 唯一允许的非方法成员是静态 logger，发现实例字段: " + f);
            assertEquals("log", f.getName());
        }
        assertEquals(1, ZRpcFrameworkInitializer.class.getDeclaredConstructors().length);
    }

    @Test
    @DisplayName("元数据可读：SimpleMetadataReader 能解析出被 @Configuration 标注的装配类")
    void configurationClassesAreReadable() throws Exception {
        SimpleMetadataReaderFactory factory = new SimpleMetadataReaderFactory();
        Class<?>[] types = new Class<?>[]{
                ZRpcAutoConfiguration.class,
                ZRpcServerAutoConfiguration.class,
                ZRpcConsumerAutoConfiguration.class,
                ZRpcRegistryAutoConfiguration.class,
                ZRpcServiceExporter.class,
                com.zifang.z.rpc.starter.config.ZRpcReferenceInjector.class
        };
        for (Class<?> type : types) {
            AnnotationMetadata md = factory.getMetadataReader(type.getName()).getAnnotationMetadata();
            assertTrue(md.isAnnotated("org.springframework.context.annotation.Configuration")
                            || md.isAnnotated("org.springframework.stereotype.Component"),
                    type + " 既不是 @Configuration 也不是 @Component，Spring 不会处理它");
        }
    }
}

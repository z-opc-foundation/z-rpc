package com.zifang.z.rpc.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExtensionLoader} —— SPI 微内核的契约面。
 * <p>
 * 夹具接口与资源文件都在本模块 test 目录内，不依赖任何其它 z-rpc 模块。
 */
class ExtensionLoaderTest {

    // ====================== getExtensionLoader 的三道前置校验 ======================

    @Test
    @DisplayName("null 接口被拒")
    void nullTypeRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ExtensionLoader.getExtensionLoader(null));
        assertTrue(e.getMessage().contains("Extension type == null"), e.getMessage());
    }

    @Test
    @DisplayName("非接口被拒：是 class 也不行")
    void nonInterfaceRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ExtensionLoader.getExtensionLoader(DemoAlpha.class));
        assertTrue(e.getMessage().contains("is not an interface"), e.getMessage());
    }

    @Test
    @DisplayName("接口存在但没打 @SPI 被拒 —— 这正是 Cluster/LoadBalance/Serialization/RegistryService 的死因")
    void missingSpiAnnotationRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ExtensionLoader.getExtensionLoader(PlainInterface.class));
        assertTrue(e.getMessage().contains("is not annotated with @SPI"), e.getMessage());
        // 猎物：同一个包里打了 @SPI 的接口就能拿到 loader
        assertNotNull(ExtensionLoader.getExtensionLoader(DemoSPI.class));
    }

    @Test
    @DisplayName("loader 按接口全局单例")
    void loaderIsCachedPerType() {
        assertSame(ExtensionLoader.getExtensionLoader(DemoSPI.class),
                ExtensionLoader.getExtensionLoader(DemoSPI.class));
        assertSame(ExtensionLoader.getExtensionLoader(ColdDemoSPI.class),
                ExtensionLoader.getExtensionLoader(ColdDemoSPI.class));
    }

    // ====================== 资源文件解析 ======================

    @Test
    @DisplayName("META-INF/z-rpc/<接口全名> 的三个 key 全部加载，注释与空行被跳过")
    void loadsEveryNameFromResourceFile() {
        ExtensionLoader<DemoSPI> loader = ExtensionLoader.getExtensionLoader(DemoSPI.class);
        List<String> names = loader.getExtensionNames();
        names.sort(java.util.Collections.reverseOrder());
        assertEquals(Arrays.asList("gamma", "beta", "alpha"), names, names.toString());
    }

    @Test
    @DisplayName("按 name 取实例，且同名实例被缓存")
    void getExtensionReturnsCachedInstance() {
        ExtensionLoader<DemoSPI> loader = ExtensionLoader.getExtensionLoader(DemoSPI.class);
        DemoSPI alpha = loader.getExtension("alpha");
        assertEquals("alpha", alpha.mark());
        assertSame(alpha, loader.getExtension("alpha"), "同名扩展必须复用实例");
        assertSame("beta", loader.getExtension("beta").mark().intern());
        assertEquals("gamma", loader.getExtension("gamma").mark());
    }

    @Test
    @DisplayName("@SPI 的 value 就是默认扩展名；\"true\" 是解析到默认的别名")
    void defaultNameAndTrueAlias() {
        ExtensionLoader<DemoSPI> loader = ExtensionLoader.getExtensionLoader(DemoSPI.class);
        assertEquals("alpha", loader.getDefaultExtensionName());
        assertEquals("alpha", loader.getDefaultExtension().mark());
        assertSame(loader.getDefaultExtension(), loader.getExtension("true"),
                "\"true\" 必须走 getDefaultExtension()");
    }

    @Test
    @DisplayName("hasExtension：命中为真、未命中为空串一律为假")
    void hasExtensionContract() {
        ExtensionLoader<DemoSPI> loader = ExtensionLoader.getExtensionLoader(DemoSPI.class);
        assertTrue(loader.hasExtension("beta"));
        assertFalseName(loader);
    }

    private static void assertFalseName(ExtensionLoader<DemoSPI> loader) {
        assertTrue(!loader.hasExtension("nope"));
        assertTrue(!loader.hasExtension(""));
        assertTrue(!loader.hasExtension(null));
    }

    // ====================== 失败路径 ======================

    @Test
    @DisplayName("未知 name 抛 IllegalStateException，而不是返回 null")
    void unknownNameThrows() {
        ExtensionLoader<DemoSPI> loader = ExtensionLoader.getExtensionLoader(DemoSPI.class);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> loader.getExtension("nope"));
        assertTrue(e.getMessage().contains("No such extension: nope"), e.getMessage());
    }

    @Test
    @DisplayName("空 name / null name 直接抛 IllegalArgumentException")
    void blankNameThrows() {
        ExtensionLoader<DemoSPI> loader = ExtensionLoader.getExtensionLoader(DemoSPI.class);
        assertThrows(IllegalArgumentException.class, () -> loader.getExtension(""));
        assertThrows(IllegalArgumentException.class, () -> loader.getExtension(null));
    }

    @Test
    @DisplayName("资源文件指向非实现类：IllegalStateException 直接冒泡")
    void notAssignableFailsLoudly() {
        ExtensionLoader<BadAssignSPI> loader = ExtensionLoader.getExtensionLoader(BadAssignSPI.class);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> loader.getExtensionNames());
        assertTrue(e.getMessage().contains("is not assignable to"), e.getMessage());
    }

    @Test
    @DisplayName("资源文件指向不存在的类：包装成 Failed to load extension class")
    void missingClassFailsLoudly() {
        ExtensionLoader<MissingClassSPI> loader = ExtensionLoader.getExtensionLoader(MissingClassSPI.class);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> loader.getExtensionNames());
        assertTrue(e.getMessage().contains("Failed to load extension class"), e.getMessage());
        assertTrue(e.getCause() instanceof ClassNotFoundException, String.valueOf(e.getCause()));
    }

    @Test
    @DisplayName("有 @SPI 但没有资源文件：只有 WARN，names 为空、默认为 null、取任何扩展都会抛")
    void spiWithoutResourceFileIsSilentlyEmpty() {
        ExtensionLoader<NoResourceSPI> loader = ExtensionLoader.getExtensionLoader(NoResourceSPI.class);
        assertTrue(loader.getExtensionNames().isEmpty(), "没有资源文件应当得到空集合");
        assertEquals("", loader.getDefaultExtensionName(),
                "@SPI 不带 value 时默认名是空串而不是 null，getDefaultExtension() 才因此返回 null");
        assertNull(loader.getDefaultExtension());
        assertTrue(!loader.hasExtension("anything"));
        assertThrows(IllegalStateException.class, () -> loader.getExtension("anything"));
    }

    @Test
    @DisplayName("对外暴露的 classes map 是不可修改视图")
    void classesMapIsUnmodifiable() {
        Map<String, Class<?>> map = ExtensionLoader.getExtensionLoader(DemoSPI.class).getExtensionClassesMap();
        assertEquals(3, map.size());
        assertEquals(DemoBeta.class, map.get("beta"));
        assertThrows(UnsupportedOperationException.class, () -> map.put("x", DemoAlpha.class));
    }

    // ====================== @Activate ======================

    @Test
    @DisplayName("冷加载器上 getActivateExtension 就会触发加载并按 order 升序返回")
    void activateExtensionLoadsOnColdLoader() {
        ExtensionLoader<ColdDemoSPI> loader = ExtensionLoader.getExtensionLoader(ColdDemoSPI.class);

        // 此前这里必须先调 getExtensionNames() 才拿得到东西；修好后冷 loader 直接可用
        List<ColdDemoSPI> consumer = loader.getActivateExtension("consumer");
        assertEquals(2, consumer.size(),
                "consumer 组应当直接看见 ColdA + ColdB —— getActivateExtension() 现在自己触发加载");
        assertEquals(Arrays.asList("coldB", "coldA"), marks(consumer),
                "@Activate.order 10 < 20，排序必须按 order 升序");

        List<ColdDemoSPI> provider = loader.getActivateExtension("provider");
        assertEquals(1, provider.size(), "ColdB 声明了 provider 组");
        assertEquals("coldB", provider.get(0).mark());

        assertTrue(loader.getActivateExtension("nobody").isEmpty());
    }

    private static List<String> marks(List<ColdDemoSPI> exts) {
        List<String> out = new java.util.ArrayList<>();
        for (ColdDemoSPI e : exts) out.add(e.mark());
        return out;
    }

    // ====================== 工厂/注解本身 ======================

    @Test
    @DisplayName("@SPI 的 value 默认是空串，且只能贴在类型上、RUNTIME 保留")
    void spiAnnotationShape() throws Exception {
        assertEquals("", SPI.class.getMethod("value").getDefaultValue());
        java.lang.annotation.Target t = SPI.class.getAnnotation(java.lang.annotation.Target.class);
        assertEquals(1, t.value().length);
        assertEquals(java.lang.annotation.ElementType.TYPE, t.value()[0]);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME,
                SPI.class.getAnnotation(java.lang.annotation.Retention.class).value());
        // 猎物：DemoSPI 用自己的 value 覆盖了默认
        assertEquals("alpha", DemoSPI.class.getAnnotation(SPI.class).value());
    }

    @Test
    @DisplayName("@Activate 的三个属性默认值")
    void activateAnnotationShape() throws Exception {
        assertEquals(0, ((String[]) Activate.class.getMethod("group").getDefaultValue()).length);
        assertEquals(0, ((String[]) Activate.class.getMethod("value").getDefaultValue()).length);
        assertEquals(0, (int) Activate.class.getMethod("order").getDefaultValue());
        assertEquals(20, ColdA.class.getAnnotation(Activate.class).order());
        assertEquals(Arrays.asList("consumer", "provider"),
                Arrays.asList(ColdB.class.getAnnotation(Activate.class).group()));
    }

    @Test
    @DisplayName("bug_AdaptiveExtensionFactory 对普通扩展名一律返回 null：连已登记的 alpha 也取不到")
    void bug_adaptiveFactoryCannotResolvePlainSpiNames() {
        ExtensionFactory factory = new AdaptiveExtensionFactory();
        // 猎物：ExtensionLoader 自己完全能解析这个名字
        assertEquals(DemoAlpha.class, ExtensionLoader.getExtensionLoader(DemoSPI.class)
                .getExtension("alpha").getClass());
        // 而工厂包装层只会返回 null —— @Adaptive 类一个都没有，工厂等于空转
        assertNull(factory.getExtension(DemoSPI.class, "alpha"),
                "AdaptiveExtensionFactory 把解析失败降级成 null，调用方无从分辨");
        assertNull(factory.getExtension(PlainInterface.class, "whatever"));
    }
}

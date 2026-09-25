package com.zifang.z.rpc.mock;

import com.zifang.z.rpc.spi.ExtensionLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GenericService 首批测试（也是 z-rpc-mock 整模块的第一批用例）。
 * <p>
 * 这个接口自称服务于「网关、测试平台的泛化调用」，实测两条都落不了地：
 * {@code $invokeMap} 只转发 {@code values()}，参数名在转发过程中被丢掉；
 * paramTypes 取的是实体的运行时类，而不是声明类型；而且整模块没有任何实现、
 * 没有 {@code @SPI}、没有 META-INF 登记表，全库 0 处引用（pom 里承诺的
 * MockClusterInvoker / PojoUtils 两个类根本不存在）。
 */
class GenericServiceTest {

    /** 记录收到的三元组。 */
    private static class Recorder implements GenericService {
        final List<String> methods = new ArrayList<>();
        final List<String[]> paramTypes = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>();
        final Object reply;

        Recorder(Object reply) {
            this.reply = reply;
        }

        @Override
        public Object $invoke(String method, String[] pts, Object[] as) {
            methods.add(method);
            paramTypes.add(pts);
            args.add(as);
            return reply;
        }

        String[] lastTypes() {
            return paramTypes.get(paramTypes.size() - 1);
        }

        Object[] lastArgs() {
            return args.get(args.size() - 1);
        }
    }

    interface Animal {}

    static class Dog implements Animal {
        @Override
        public String toString() {
            return "dog";
        }
    }

    @Test
    @DisplayName("$invoke 把三元组原样交给实现方")
    void invokeForwardsTheTripleVerbatim() {
        Recorder r = new Recorder("pong");
        String[] pts = new String[]{"java.lang.String", "int"};
        Object[] as = new Object[]{"zifang", 7};
        Object out = r.$invoke("ping", pts, as);
        // 猎物：一次调用，一条记录，返回值不动
        assertEquals(1, r.methods.size());
        assertEquals("ping", r.methods.get(0));
        assertSame(pts, r.paramTypes.get(0));
        assertSame(as, r.args.get(0));
        assertEquals("pong", out);
    }

    @Test
    @DisplayName("$invokeMap 只转发 values()，键名在实现方那里彻底消失")
    void invokeMapForwardsValuesAndDropsKeys() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "zifang");
        params.put("age", 30);
        // 猎物：调用方这边的 map 当然认得键
        assertTrue(params.containsKey("name"));
        assertEquals(Arrays.asList("zifang", 30), new ArrayList<>(params.values()));

        Recorder r = new Recorder("ok");
        r.$invokeMap("greet", params);
        assertArrayEquals(new Object[]{"zifang", 30}, r.lastArgs());
        assertEquals(1, r.methods.size());
        assertEquals("greet", r.methods.get(0));
        // 实测：实现方拿到的只有位置和运行时类型，一个参数名都没有
        assertEquals(Arrays.asList("java.lang.String", "java.lang.Integer"),
                Arrays.asList(r.lastTypes()));
    }

    @Test
    @DisplayName("LinkedHashMap 的插入顺序就是实参顺序：换个顺序就换个语义")
    void argumentOrderFollowsMapIterationOrder() {
        Map<String, Object> asc = new LinkedHashMap<>();
        asc.put("first", "A");
        asc.put("second", "B");
        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("second", "B");
        desc.put("first", "A");

        Recorder r = new Recorder(null);
        r.$invokeMap("m", asc);
        assertArrayEquals(new Object[]{"A", "B"}, r.lastArgs());
        r.$invokeMap("m", desc);
        // 两个 map 内容相同，实参数组却是反的 —— 键名不参与匹配，只有位置参与
        assertArrayEquals(new Object[]{"B", "A"}, r.lastArgs());
    }

    @Test
    @DisplayName("bug_ paramTypes 取实体的运行时类：子类会被报成子类，null 只能报 Object")
    void bug_paramTypesAreDerivedFromRuntimeClasses() {
        Animal a = new Dog();
        Recorder r = new Recorder(null);
        r.$invokeMap("feed", singletonParams(a));
        // 实测行为：服务端按声明类型找方法时会找不到
        assertEquals(Dog.class.getName(), r.lastTypes()[0]);
        assertNotEquals(Animal.class.getName(), r.lastTypes()[0], "声明类型从未被使用");

        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("maybe", null);
        r.$invokeMap("nvl", withNull);
        assertEquals("java.lang.Object", r.lastTypes()[0]);
        assertNull(r.lastArgs()[0]);
    }

    private static Map<String, Object> singletonParams(Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("animal", v);
        return m;
    }

    @Test
    @DisplayName("bug_ null 参数表和空 map 是两种形状：(null,null) vs 两个零长数组")
    void bug_nullAndEmptyMapTakeDifferentShapes() {
        Recorder r = new Recorder(null);
        r.$invokeMap("m", null);
        // 猎物：方法名照常转发
        assertEquals("m", r.methods.get(0));
        assertNull(r.lastTypes(), "params==null 走的是 $invoke(method, null, null)");
        assertNull(r.lastArgs());

        r.$invokeMap("m", new HashMap<String, Object>());
        assertEquals(0, r.lastTypes().length);
        assertEquals(0, r.lastArgs().length);
        // 实现方要用两条不同的分支才接得住「没有参数」这件事
        assertFalse(r.lastTypes() == null);
    }

    @Test
    @DisplayName("接口形状：一个抽象方法 + 一个 default 便捷方法，共两个")
    void interfaceShape() {
        assertTrue(GenericService.class.isInterface());
        Method[] ms = GenericService.class.getDeclaredMethods();
        assertEquals(2, ms.length);
        int abstracts = 0;
        int defaults = 0;
        for (Method m : ms) {
            if (Modifier.isAbstract(m.getModifiers())) {
                abstracts++;
                assertEquals("$invoke", m.getName());
            } else {
                defaults++;
                assertTrue(m.isDefault(), m.getName());
                assertEquals("$invokeMap", m.getName());
            }
        }
        assertEquals(1, abstracts);
        assertEquals(1, defaults);
    }

    @Test
    @DisplayName("bug_ GenericService 不是扩展点：没有 @SPI，也没有登记表")
    void bug_genericServiceIsNotAnExtensionPoint() {
        assertNull(GenericService.class.getAnnotation(com.zifang.z.rpc.spi.SPI.class));
        assertNull(Thread.currentThread().getContextClassLoader()
                .getResource("META-INF/z-rpc/com.zifang.z.rpc.mock.GenericService"));
        // 猎物：同 classpath 上打了 @SPI 的接口两样都有
        assertNotNull(com.zifang.z.rpc.api.ProxyFactory.class
                .getAnnotation(com.zifang.z.rpc.spi.SPI.class));
        assertNotNull(Thread.currentThread().getContextClassLoader()
                .getResource("META-INF/z-rpc/com.zifang.z.rpc.api.ProxyFactory"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ExtensionLoader.getExtensionLoader(GenericService.class));
        assertTrue(e.getMessage().contains("@SPI"), e.getMessage());
    }

    @Test
    @DisplayName("bug_ pom 承诺的 MockClusterInvoker / PojoUtils 在本模块不存在")
    void bug_advertisedMockClassesDoNotExist() {
        // 猎物：本模块唯一的类型是 GenericService
        assertNotNull(GenericService.class.getName());
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.zifang.z.rpc.mock.MockClusterInvoker"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.zifang.z.rpc.mock.PojoUtils"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.zifang.z.rpc.mock.MockFilter"));
        // 猎物：Class.forName 的机制是通的（同模块这个类就找得到）
        try {
            assertSame(GenericService.class, Class.forName("com.zifang.z.rpc.mock.GenericService"));
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Class.forName 不该失灵", e);
        }
    }
}

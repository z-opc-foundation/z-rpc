package com.zifang.z.rpc.starter.docs;

import com.zifang.z.rpc.common.ProtocolConstants;
import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * README 的契约测试。
 * <p>
 * 兑现侧（行为）已有 500+ 条用例钉住，这一层钉的是广告侧：README 写出的模块数、
 * SPI 注册数与 key、yaml key、端口、类名、链接、许可证、帧头长度，全部用同一份机械谓词复算。
 * <p>
 * 每条负向断言都在同一条用例里带一个 planted fixture —— 谓词抓不到猎物的话，
 * 它对真 README 的放行毫无意义。
 */
class ReadmeContractTest {

    // ---------------------------------------------------------------- 基础设施

    private static File repoRoot() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (new File(dir, "README.md").isFile() && new File(dir, "z-rpc-common/pom.xml").isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("找不到仓库根（README.md + z-rpc-common/pom.xml），起点 "
                + System.getProperty("user.dir"));
    }

    private static String text(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String readme() throws IOException {
        return text(new File(repoRoot(), "README.md"));
    }

    private static List<String> linesOf(String s) {
        List<String> out = new ArrayList<String>();
        for (String ln : s.split("\n", -1)) {
            out.add(ln.endsWith("\r") ? ln.substring(0, ln.length() - 1) : ln);
        }
        return out;
    }

    /** [语言, 正文]。围栏不成对直接判红，而不是静默少算一段。 */
    private static List<String[]> codeFences(String s) {
        List<String[]> out = new ArrayList<String[]>();
        boolean in = false;
        String lang = null;
        StringBuilder sb = new StringBuilder();
        for (String ln : linesOf(s)) {
            if (!in && ln.startsWith("```")) {
                in = true;
                lang = ln.substring(3).trim();
                sb.setLength(0);
            } else if (in && ln.trim().equals("```")) {
                out.add(new String[] {lang, sb.toString()});
                in = false;
            } else if (in) {
                sb.append(ln).append('\n');
            }
        }
        assertFalse(in, "README 有未闭合的三反引号围栏，之后的内容会被解析器整段吞掉");
        return out;
    }

    private static List<String> fencesOf(String s, String lang) {
        List<String> out = new ArrayList<String>();
        for (String[] f : codeFences(s)) {
            if (lang.equals(f[0])) {
                out.add(f[1]);
            }
        }
        return out;
    }

    private static List<String> findAll(Pattern p, String s) {
        List<String> out = new ArrayList<String>();
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    // ---------------------------------------------------------------- R1/R2 模块表

    private static final Pattern MODULE_NAME = Pattern.compile("^z-rpc-[a-z-]+$");

    private static final Pattern POM_COMMENT = Pattern.compile("(?s)<!--.*?-->");

    private static final Pattern MODULE_TAG = Pattern.compile("<module>([^<]+)</module>");

    /** “模块与实际装配状态”表的数据行：[模块名, reactor 列, 第三列原文]。 */
    private static List<String[]> moduleTableRows(String readme) {
        List<String[]> rows = new ArrayList<String[]>();
        for (String ln : linesOf(readme)) {
            if (!ln.startsWith("| `z-rpc-")) {
                continue;
            }
            String[] cols = ln.split("\\|");
            if (cols.length < 4) {
                continue;
            }
            String name = cols[1].trim().replace("`", "");
            if (MODULE_NAME.matcher(name).matches()) {
                rows.add(new String[] {name, cols[2].trim(), cols[3].trim()});
            }
        }
        return rows;
    }

    private static List<String> tagsIn(String chunk) {
        List<String> out = new ArrayList<String>();
        Matcher m = MODULE_TAG.matcher(chunk);
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }

    private static List<String> activePomModules(String pomText) {
        return tagsIn(POM_COMMENT.matcher(pomText).replaceAll(" "));
    }

    private static List<String> commentedPomModules(String pomText) {
        List<String> out = new ArrayList<String>();
        Matcher cm = POM_COMMENT.matcher(pomText);
        while (cm.find()) {
            out.addAll(tagsIn(cm.group()));
        }
        return out;
    }

    private static List<String> zRpcDirs(File root) {
        List<String> out = new ArrayList<String>();
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (k.isDirectory() && k.getName().startsWith("z-rpc-")) {
                    out.add(k.getName());
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    @Test
    @DisplayName("模块表的行等于磁盘上的 z-rpc-* 目录；✅/❌ 等于根 pom 的启用/注释；正文三个计数跟着实测走")
    void moduleTableAgreesWithDiskAndRootPom() throws IOException {
        File root = repoRoot();
        String r = readme();
        List<String> dirs = zRpcDirs(root);
        String pom = text(new File(root, "pom.xml"));
        List<String> active = activePomModules(pom);
        List<String> commented = commentedPomModules(pom);

        List<String[]> rows = moduleTableRows(r);
        assertEquals(dirs.size(), rows.size(),
                "磁盘上有 " + dirs.size() + " 个 z-rpc-* 目录，表里只有 " + rows.size() + " 行：" + names(rows));

        List<String> markedOk = new ArrayList<String>();
        for (String[] row : rows) {
            assertTrue(new File(root, row[0]).isDirectory(), "表里写了 " + row[0] + "，磁盘上没有这个目录");
            if (row[1].contains("✅")) {
                markedOk.add(row[0]);
            } else {
                assertFalse(active.contains(row[0]), "表里 " + row[0] + " 标 ❌，但根 pom 已经启用它");
            }
        }
        Collections.sort(markedOk);
        List<String> sortedActive = new ArrayList<String>(active);
        Collections.sort(sortedActive);
        assertEquals(sortedActive, markedOk, "表里 ✅ 的集合必须等于根 pom 启用的集合");

        assertTrue(r.contains("有 **" + dirs.size() + "** 个目录"),
                "正文的目录数没跟上实测（实测 " + dirs.size() + "）");
        assertTrue(r.contains("**启用 " + active.size() + " 个**"),
                "正文的启用模块数没跟上根 pom（实测 " + active.size() + "）");
        assertTrue(r.contains("另外 " + commented.size() + " 行被注释掉"),
                "正文的注释行数没跟上根 pom（实测 " + commented.size() + "）");

        // 正向对照：剥注释的谓词真分得开两类，否则上面三句可能靠“两边都空”蒙绿
        assertTrue(commented.contains("z-rpc-examples"), "z-rpc-examples 本该在被注释的那批里");
        assertFalse(active.contains("z-rpc-examples"), "active 里混进了注释掉的模块，说明 <modules> 解析坏了");
        assertTrue(active.contains("z-rpc-common") && active.contains("z-rpc-core"),
                "active 读不出正常模块，说明解析根本没跑通");
    }

    @Test
    @DisplayName("模块表第三列点名的每个类，都真的在“那个模块”的 src/main/java 里")
    void classNamesClaimedByModuleTableExistInThatModule() throws IOException {
        File root = repoRoot();
        List<String> missing = phantomClassNames(readme(), root);
        assertEquals(Collections.<String>emptyList(), missing,
                "模块表把仓里不存在的类说成「模块里真实存在的东西」");

        List<String> caught = phantomClassNames("| `z-rpc-common` | ✅ | `URL` / `NoSuchClassRhyme` |\n", root);
        assertEquals(1, caught.size(), "谓词抓不到捏造类名，上面对 README 的放行没有意义");
        assertTrue(caught.get(0).contains("NoSuchClassRhyme"), caught.toString());
    }

    /** 括号里是「这个类其实在别的模块」的旁注，不算本模块的兑现。 */
    private static List<String> phantomClassNames(String readme, File root) {
        List<String> out = new ArrayList<String>();
        for (String[] row : moduleTableRows(readme)) {
            String col3 = row[2].replaceAll("（[^）]*）", "");
            Matcher m = Pattern.compile("`@?([A-Z][A-Za-z0-9_]*)`").matcher(col3);
            File src = new File(new File(root, row[0]), "src/main/java");
            while (m.find()) {
                if (!hasSourceFileFor(src, m.group(1))) {
                    out.add(row[0] + " 缺 " + m.group(1) + ".java");
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    private static boolean hasSourceFileFor(File dir, String simpleName) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return false;
        }
        for (File k : kids) {
            if (k.isDirectory()) {
                if (hasSourceFileFor(k, simpleName)) {
                    return true;
                }
            } else if (k.getName().equals(simpleName + ".java")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- R3 SPI 表

    private static final Pattern SPI_ROW = Pattern.compile(
            "^\\| `(com\\.zifang\\.z\\.rpc\\.[A-Za-z0-9_.]+)` \\|([^|]*)\\|([^|]*)\\|(.*)\\|$");

    private static List<String[]> spiTableRows(String readme) {
        List<String[]> rows = new ArrayList<String[]>();
        for (String ln : linesOf(readme)) {
            Matcher m = SPI_ROW.matcher(ln);
            if (m.matches()) {
                rows.add(new String[] {m.group(1), m.group(3).trim(), m.group(4).trim()});
            }
        }
        return rows;
    }

    /** key=value 行，跳过 # 注释 —— 与 ExtensionLoader 读清单的口径一致。 */
    private static Map<String, String> registrationEntries(File root, String fqn) throws IOException {
        Map<String, String> out = new HashMap<String, String>();
        for (File f : spiResourceFiles(root, fqn)) {
            for (String ln : linesOf(text(f))) {
                String t = ln.trim();
                if (t.isEmpty() || t.startsWith("#") || t.indexOf('=') < 0) {
                    continue;
                }
                out.put(t.substring(0, t.indexOf('=')).trim(), t.substring(t.indexOf('=') + 1).trim());
            }
        }
        return out;
    }

    private static List<File> spiResourceFiles(File root, String fqn) {
        List<File> out = new ArrayList<File>();
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (!k.isDirectory()) {
                    continue;
                }
                for (String side : new String[] {"main", "test"}) {
                    File f = new File(resourcesDir(k, side), "META-INF/z-rpc/" + fqn);
                    if (f.isFile()) {
                        out.add(f);
                    }
                }
            }
        }
        return out;
    }

    private static File resourcesDir(File module, String side) {
        return new File(new File(new File(module, "src"), side), "resources");
    }

    @Test
    @DisplayName("SPI 表的注册数与 key 逐个等于 META-INF/z-rpc 清单，清单指向的实现类也真在磁盘上")
    void spiTableCountsAndKeysMatchRegistrationFiles() throws IOException {
        File root = repoRoot();
        List<String[]> rows = spiTableRows(readme());
        assertEquals(7, rows.size(), "SPI 表该有 7 个扩展点，实际解析到 " + rows.size() + " 行——先确认表格形状没被改坏");

        for (String[] row : rows) {
            String fqn = row[0];
            Map<String, String> real = registrationEntries(root, fqn);
            assertFalse(real.isEmpty(), fqn + " 在 META-INF/z-rpc/ 下没有清单文件（或全是注释）");

            int claimed = Integer.parseInt(row[1].replaceAll("[^0-9]", ""));
            assertEquals(real.size(), claimed, fqn + "：表的注册数 ≠ 清单文件的非注释行数");

            Set<String> claimedKeys = new LinkedHashSet<String>();
            Matcher km = Pattern.compile("`([a-z0-9\\-]+)`").matcher(row[2]);
            while (km.find()) {
                claimedKeys.add(km.group(1));
            }
            // 只比集合不比顺序：README 那列的枚举次序不是承诺，ExtensionLoader 也不按它返回
            assertEquals(new LinkedHashSet<String>(real.keySet()), claimedKeys,
                    fqn + "：表的 key 集合与清单不一致，清单=" + real.keySet());

            for (Map.Entry<String, String> e : real.entrySet()) {
                assertTrue(hasSourceFile(root, e.getValue()),
                        fqn + " 注册了 " + e.getValue() + "，但任何模块的 src/main/java 里都没有它");
            }
        }

        // 正向对照：RegistryService 清单是 5 行注释 + 1 行真注册，只数真注册的那个口径必须成立
        Map<String, String> registry = registrationEntries(root, "com.zifang.z.rpc.registry.RegistryService");
        assertEquals(Collections.singleton("in-memory"), registry.keySet(),
                "被注释的 z-config / zknaming 混进了计数，上一轮 README 就是被这两行骗成 3 个注册项");
    }

    private static boolean hasSourceFile(File root, String fqn) {
        return sourceOf(root, fqn) != null;
    }

    @Test
    @DisplayName("SPI 表点名的 7 个接口真有带 @SPI 的那份源码；一份以上重复时 README 必须点名 N12")
    void spiInterfacesAreAnnotatedAndHaveTheirOwnResourceFile() throws IOException {
        File root = repoRoot();
        String r = readme();
        int shadowed = 0;
        for (String[] row : spiTableRows(r)) {
            String fqn = row[0];
            List<File> copies = sourcesOf(root, fqn);
            assertFalse(copies.isEmpty(), fqn + " 没有源文件");

            List<String> annotated = new ArrayList<String>();
            for (File f : copies) {
                String body = stripComments(text(f));
                if (body.contains("@SPI")
                        && Pattern.compile("public\\s+interface\\s+" + simple(fqn) + "\\b").matcher(body).find()) {
                    annotated.add(moduleOf(root, f));
                }
            }
            assertFalse(annotated.isEmpty(), fqn + " 的每一份拷贝都缺 @SPI —— 报告 C2 的原症状就是"
                    + "「注解缺失让整张登记表变成死文件」");
            if (copies.size() > 1) {
                // N12：同 FQN 在两个模块各编译一份，加载哪一份取决于 classpath 顺序。
                // README 的 ✅ 只在"带注解那份赢"时成立，所以表旁边必须有那句警告。
                shadowed++;
                assertTrue(r.contains("N12"), fqn + " 有 " + copies.size() + " 份源码（带 @SPI 的只有 "
                        + annotated.size() + " 份），README 没点名 N12 —— 读者会以为这个扩展点在 starter 里也拿得到");
            }
        }
        assertEquals(1, shadowed, "split package 的数量变了（实测只有 com.zifang.z.rpc.cluster.Cluster 这一族）——"
                + "若 N12 已收口，README 那句 N12 警告就该撤");

        File url = sourceOf(root, "com.zifang.z.rpc.common.URL");
        assertNotNull(url, "连 com.zifang.z.rpc.common.URL 都找不到，说明 sourcesOf 本身坏了");
        assertFalse(stripComments(text(url)).contains("@SPI"),
                "URL 若被判成 @SPI 接口，上面整轮的放行就是谓词太松");
    }

    private static String moduleOf(File root, File f) {
        String rel = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
        int i = rel.indexOf('/');
        return i < 0 ? rel : rel.substring(0, i);
    }

    private static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static File sourceOf(File root, String fqn) {
        List<File> all = sourcesOf(root, fqn);
        return all.isEmpty() ? null : all.get(0);
    }

    /** 同一个 FQN 可能在两个模块各有一份源码（N12 的 split package），全部交出来。 */
    private static List<File> sourcesOf(File root, String fqn) {
        String rel = fqn.replace('.', '/') + ".java";
        List<File> out = new ArrayList<File>();
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File k : kids) {
                File cand = new File(new File(k, "src/main/java"), rel);
                if (cand.isFile()) {
                    out.add(cand);
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    private static String simple(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    // ---------------------------------------------------------------- R4 yaml 配置面

    private static final Pattern YAML_LINE = Pattern.compile("^( *)([A-Za-z0-9_.\\-]+):(.*)$");

    private static final Pattern ZRPC_PATH = Pattern.compile("z\\.rpc\\.[A-Za-z0-9_.\\-]+");

    /** 从 yaml 片段里抽出叶子 key 的全路径（含前两层 z / rpc）。 */
    private static List<String> yamlLeafPaths(String yaml) {
        List<String> out = new ArrayList<String>();
        List<Integer> indents = new ArrayList<Integer>();
        List<String> names = new ArrayList<String>();
        for (String raw : linesOf(yaml)) {
            int hash = raw.indexOf(" #");
            String ln = (hash >= 0 ? raw.substring(0, hash) : raw).replaceAll("\\s+$", "");
            if (ln.trim().isEmpty()) {
                continue;
            }
            Matcher m = YAML_LINE.matcher(ln);
            if (!m.matches()) {
                continue;
            }
            int indent = m.group(1).length();
            String value = m.group(3) == null ? "" : m.group(3).trim();
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                names.remove(names.size() - 1);
            }
            indents.add(indent);
            names.add(m.group(2));
            if (!value.isEmpty()) {
                StringBuilder path = new StringBuilder();
                for (String n : names) {
                    path.append(path.length() == 0 ? n : "." + n);
                }
                out.add(path.toString());
            }
        }
        return out;
    }

    private static String prefix() {
        return ZRpcProperties.class.getAnnotation(ConfigurationProperties.class).prefix();
    }

    /** ZRpcProperties 的整棵字段树；前缀取自 @ConfigurationProperties，不硬编码。 */
    private static Set<String> declaredPropertyPaths() {
        Set<String> out = new LinkedHashSet<String>();
        walkProperties(ZRpcProperties.class, prefix(), out);
        return out;
    }

    private static void walkProperties(Class<?> clazz, String path, Set<String> sink) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            String child = path + "." + f.getName();
            sink.add(child);
            if (isPropertyGroup(f.getType())) {
                walkProperties(f.getType(), child, sink);
            }
        }
    }

    private static boolean isPropertyGroup(Class<?> t) {
        return t.getName().startsWith(ZRpcProperties.class.getName() + "$");
    }

    private static String canonical(String s) {
        return s.toLowerCase().replace("-", "").replace("_", "");
    }

    /** 逐个 segment 对着字段树走；叶子后面还挂东西也算走不通（server.threads.boss 这类）。 */
    private static boolean resolvesAsProperty(String dotted) {
        String p = prefix();
        if (!dotted.startsWith(p + ".")) {
            return false;
        }
        String[] segs = dotted.substring(p.length() + 1).split("\\.");
        Class<?> current = ZRpcProperties.class;
        for (int i = 0; i < segs.length; i++) {
            Field hit = null;
            for (Field f : current.getDeclaredFields()) {
                if (!f.isSynthetic() && canonical(f.getName()).equals(canonical(segs[i]))) {
                    hit = f;
                    break;
                }
            }
            if (hit == null) {
                return false;
            }
            if (i < segs.length - 1) {
                if (!isPropertyGroup(hit.getType())) {
                    return false;
                }
                current = hit.getType();
            }
        }
        return true;
    }

    private static List<String> unknownKeysIn(String yaml) {
        List<String> out = new ArrayList<String>();
        for (String p : yamlLeafPaths(yaml)) {
            if (!resolvesAsProperty(p)) {
                out.add(p);
            }
        }
        return out;
    }

    @Test
    @DisplayName("README 那份 application.yml 里每个 key 都是 ZRpcProperties 的真实字段")
    void yamlKeysShownInReadmeAllResolveInZRpcProperties() throws IOException {
        List<String> yaml = fencesOf(readme(), "yaml");
        assertEquals(1, yaml.size(), "README 该只有一份 ```yaml 接入示例；多份会让本条只查到第一份");

        List<String> paths = yamlLeafPaths(yaml.get(0));
        assertTrue(paths.size() >= 15, "yaml 叶子只解析出 " + paths.size() + " 个，解析器没跑通");
        assertEquals(Collections.<String>emptyList(), unknownKeysIn(yaml.get(0)),
                "README 写了 ZRpcProperties 里没有的 key：Spring Boot 静默丢掉，用户以为生效了");

        // 正向对照：字段树真的展开了，且同一个谓词对捏造 key 说不
        Set<String> declared = declaredPropertyPaths();
        assertTrue(declared.contains("z.rpc.application.organization"), "字段树遍历坏了：" + declared);
        assertTrue(declared.contains("z.rpc.server.ioThreads") && declared.contains("z.rpc.protocol.compressThreshold"),
                "嵌套类没展开，字段树是空壳");
        assertTrue(unknownKeysIn("z:\n  rpc:\n    server:\n      boss: 1\n      port: 20880\n")
                .contains("z.rpc.server.boss"), "谓词抓不到假 key，上面的放行没有意义");
    }

    @Test
    @DisplayName("README 点名「不是 ZRpcProperties 字段」的那串 key，确实一个都解析不出来")
    void keysReadmeCallsDeadAreActuallyDead() throws IOException {
        String r = readme();
        int from = r.indexOf("上一版 README 这里的");
        int to = r.indexOf("都不是 `ZRpcProperties` 的字段");
        assertTrue(from >= 0 && to > from,
                "README 少了那句死 key 清单：这条用例的对照面没了");

        List<String> checked = new ArrayList<String>();
        List<String> alive = new ArrayList<String>();
        for (String hit : findAll(ZRPC_PATH, r.substring(from, to))) {
            checked.add(hit);
            if (resolvesAsProperty(hit)) {
                alive.add(hit);
            }
        }
        assertTrue(checked.size() >= 10, "只扫到 " + checked.size() + " 个 key，抽取正则没覆盖那句的写法：" + checked);
        assertEquals(Collections.<String>emptyList(), alive,
                "README 把活的 key 说成死的，会误导用户去改文档而不是改配置");

        // 正向对照：同一个谓词必须认定 z.rpc.server.port 是活的
        assertTrue(resolvesAsProperty("z.rpc.server.port"), "谓词把真字段判成死的，上面的通过是假的");
        assertFalse(resolvesAsProperty("z.rpc.port"), "谓词把 z.rpc.port 判成活的，与实测矛盾");
    }

    // ---------------------------------------------------------------- R5 路径 / 端口 / 许可

    private static final Pattern MD_LINK = Pattern.compile("\\]\\(([^)h#\\s][^)]*)\\)");

    private static final Pattern META_INF = Pattern.compile("`META-INF/[A-Za-z0-9_.\\-/]+`");

    private static final String REAL_SPI_DIR = "META-INF/z-rpc";

    /**
     * 反引号里的 META-INF 路径：写对目录名的必须在磁盘上真存在；写错目录名（`META-INF/zrpc/`）
     * 只允许出现在"纠正上一版"的那句里 —— 判据是同句带「不存在」或「错」，否则就是给读者的指令。
     */
    private static List<String> badMetaInfPaths(String readme, File root) {
        List<String> out = new ArrayList<String>();
        for (String ln : linesOf(readme)) {
            Matcher m = META_INF.matcher(ln);
            while (m.find()) {
                String rel = m.group().replace("`", "");
                if (rel.startsWith(REAL_SPI_DIR)) {
                    if (!metaInfExists(root, rel)) {
                        out.add("不存在的路径 " + rel);
                    }
                } else if (!ln.contains("不存在") && !ln.contains("错")) {
                    out.add("把错目录名当指令写给了读者：" + rel);
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("README 的相对链接与 META-INF 路径都指向磁盘上真存在的东西")
    void linksAndResourcePathsResolveOnDisk() throws IOException {
        File root = repoRoot();
        String r = readme();

        assertEquals(Collections.<String>emptyList(), deadLinks(r, root), "README 有死链");
        assertTrue(MD_LINK.matcher(r).find(), "一条相对链接都没解析到，deadLinks 为空只是因为没扫任何东西");

        List<String> hits = findAll(META_INF, r);
        assertTrue(hits.size() >= 3, "README 只提到 " + hits.size() + " 个 META-INF 路径，这条用例空跑");
        assertEquals(Collections.<String>emptyList(), badMetaInfPaths(r, root),
                "上一版把目录写成 META-INF/zrpc/（少连字符），照抄的人自定义扩展静默加载不到");

        // 猎物 1：错目录名出现在没有"纠正"字样的句子里
        assertEquals(1, badMetaInfPaths("在 `META-INF/zrpc/com.zifang.z.rpc.filter.Filter` 中声明：\n", root).size(),
                "谓词抓不到 META-INF/zrpc/，上面的放行没有意义");
        // 猎物 2：目录名对、文件不存在
        assertEquals(1, badMetaInfPaths("`" + REAL_SPI_DIR + "/com.zifang.z.rpc.NonexistentSpi` 存在\n", root).size(),
                "谓词抓不到不存在的清单文件名");
    }

    private static List<String> deadLinks(String readme, File root) {
        List<String> out = new ArrayList<String>();
        Matcher m = MD_LINK.matcher(readme);
        while (m.find()) {
            String target = m.group(1);
            int hash = target.indexOf('#');
            if (hash >= 0) {
                target = target.substring(0, hash);
            }
            if (!target.isEmpty() && !new File(root, target).exists()) {
                out.add(target);
            }
        }
        return out;
    }

    private static boolean metaInfExists(File root, String rel) {
        File[] kids = root.listFiles();
        if (kids == null) {
            return false;
        }
        for (File k : kids) {
            if (!k.isDirectory()) {
                continue;
            }
            for (String side : new String[] {"main", "test"}) {
                if (new File(resourcesDir(k, side), rel).exists()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Pattern URL_PORT = Pattern.compile("http://localhost:(\\d+)");

    @Test
    @DisplayName("README 报的端口等于各配置文件自己写的端口，正文的 localhost 端口全在实测集合内")
    void everyPortQuotedInReadmeMatchesItsOwnConfigFile() throws IOException {
        File root = repoRoot();
        String r = readme();

        int admin = firstPort(new File(root, "z-rpc-admin/src/main/resources/application.yml"));
        int vite = firstPort(new File(root, "z-rpc-admin-frontend/vite.config.ts"));
        assertTrue(admin > 0 && vite > 0, "端口读取本身失败了：admin=" + admin + " vite=" + vite);

        // 端口要落在"讲这个模块的那一行"上，全文别处出现过一次不算数（变异取证：把模块表那行改成
        // 监听 9090，只要正文还有一处写着 19090，全局 contains 就永远绿）
        String adminCell = moduleRowCell(r, "z-rpc-admin");
        String viteCell = moduleRowCell(r, "z-rpc-admin-frontend");
        assertTrue(carriesPort(adminCell, admin), "模块表里 z-rpc-admin 那行没写实测端口 " + admin + "：" + adminCell);
        assertTrue(carriesPort(viteCell, vite), "模块表里 z-rpc-admin-frontend 那行没写实测端口 " + vite + "：" + viteCell);
        assertTrue(r.contains("**" + admin + "**"), "README 正文没把 admin 端口写成 **" + admin + "**");
        assertTrue(r.contains("**" + vite + "**"), "README 正文没把前端端口写成 **" + vite + "**");

        // 猎物 1：上一版的 9090 说法回到模块表里，必须判红
        assertFalse(carriesPort("Spring Boot 后端，监听 **9090**", admin),
                "把行内端口改成 9090 都不算错，这条断言就只是个装饰");
        // 猎物 2：9090 是 19090 的子串，按数字边界匹配不许把两者混为一谈
        assertTrue(carriesPort("监听 **19090** 与 20880", admin) && !carriesPort("监听 **219090**", admin),
                "数字边界没生效");

        Set<String> allowed = new LinkedHashSet<String>();
        allowed.add(String.valueOf(admin));
        allowed.add(String.valueOf(vite));

        String[][] examples = {{"user-service", "20880"}, {"order-service", null}};
        for (String[] ex : examples) {
            File yml = new File(root, "z-rpc-examples/" + ex[0] + "/src/main/resources/application.yml");
            String body = text(yml);
            int http = firstPort(yml);
            allowed.add(String.valueOf(http));
            String row = "| `z-rpc-examples/" + ex[0] + "` | " + http + " | ";
            assertTrue(r.contains(row), ex[0] + "：HTTP 端口实测 " + http + "，README 的示例表对不上");
            String rest = r.substring(r.indexOf(row) + row.length());
            String rpcCell = rest.substring(0, rest.indexOf('|')).trim();
            if (rpcCell.matches("\\d+.*")) {
                int rpc = Integer.parseInt(rpcCell.replaceAll("[^0-9].*$", ""));
                allowed.add(String.valueOf(rpc));
                assertTrue(body.contains("port: " + rpc),
                        ex[0] + "：README 的 RPC 列写 " + rpc + "，yml 里没有这个值");
            } else {
                assertTrue(body.contains("enabled: false"),
                        ex[0] + "：README 说它不起服务端，yml 里却没有 server.enabled: false");
            }
        }

        int urls = findAll(URL_PORT, r).size();
        assertTrue(urls >= 2, "README 没写 localhost URL，这条断言空跑");
        assertEquals(Collections.<String>emptyList(), bogusUrlPorts(r, allowed),
                "README 链到仓内没有任何配置支撑的端口；实测集合=" + allowed);

        // 猎物：凭空端口必须被同一个谓词抓出来
        List<String> caught = bogusUrlPorts("http://localhost:8123\n" + r, allowed);
        assertEquals(1, caught.size(), "谓词抓不到 8123，上面的放行没有意义");
    }

    private static List<String> bogusUrlPorts(String text, Set<String> allowed) {
        List<String> out = new ArrayList<String>();
        Matcher m = URL_PORT.matcher(text);
        while (m.find()) {
            if (!allowed.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private static int firstPort(File f) throws IOException {
        Matcher m = Pattern.compile("port:\\s*\"?(\\d+)").matcher(text(f));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * 出现 "Apache License" 的行：写成 markdown 链接（`[Apache License 2.0](LICENSE)`）就是**声明**，直接判红；
     * 只剩引用形式（带引号）且同句带「上一版」「错」才当纠正句放过。
     * 只按"同句有没有错字"放行是不够的 —— 取证那一轮我往这句中间插了一行猎物，
     * 尾巴上原有的"是错的"三个字把整行洗白了（见报告 §11）。
     */
    private static List<String> apacheClaims(String readme) {
        List<String> out = new ArrayList<String>();
        for (String ln : linesOf(readme)) {
            if (!ln.contains("Apache License")) {
                continue;
            }
            if (ln.contains("[Apache License")) {
                out.add("以链接形式声明 Apache：" + ln.trim());
            } else if (!ln.contains("上一版") && !ln.contains("错")) {
                out.add(ln.trim());
            }
        }
        return out;
    }

    @Test
    @DisplayName("许可证一节跟着 LICENSE 文件走，README 不再自称 Apache")
    void licenseSectionMatchesLicenseFile() throws IOException {
        String firstLine = linesOf(text(new File(repoRoot(), "LICENSE"))).get(0).trim();
        assertFalse(firstLine.isEmpty(), "LICENSE 第一行是空的");
        String r = readme();
        assertTrue(r.contains(firstLine), "README 没写出 LICENSE 实际的许可证名：" + firstLine);
        assertFalse(firstLine.startsWith("Apache"), "LICENSE 变成了 Apache，README 那句「跟着 LICENSE 走」得改");
        assertEquals(Collections.<String>emptyList(), apacheClaims(r), "README 里有 Apache 许可声明残留");

        // 正向对照：那句"上一版底部写的 Apache 是错的"确实被扫到了，只是被认成纠正句 ——
        // 若扫描根本没命中，apacheClaims 为空只是因为没看东西
        int mentions = 0;
        for (String ln : linesOf(r)) {
            if (ln.contains("Apache License")) {
                mentions++;
            }
        }
        assertEquals(1, mentions, "README 里 Apache 字样出现 " + mentions + " 次，这条用例的对照面变了");
        // 猎物：把同一句的"上一版…是错的"壳摘掉，谓词必须立刻判红
        assertEquals(1, apacheClaims("[Apache License 2.0](LICENSE)\n").size(),
                "谓词抓不到裸的 Apache 声明，上面那句放行没有意义");
    }

    @Test
    @DisplayName("README 说的 26 字节定长头等于 ProtocolConstants.HEADER_LENGTH，旧的 24 写法不再出现")
    void headerLengthClaimEqualsTheConstant() throws IOException {
        int h = ProtocolConstants.HEADER_LENGTH;
        String r = readme();
        assertTrue(r.contains("HEADER_LENGTH = " + h), "README 没按实测的 " + h + " 写常量值");
        assertTrue(r.contains(h + " 字节定长头"), "README 少了「" + h + " 字节定长头」这句主张");
        assertFalse(r.contains((h - 2) + " 字节定长头"),
                "旧的 24 字节说法回来了 —— 半包停在 24/25 字节时解码器抛 IndexOutOfBoundsException（报告 H1）");
        assertFalse(r.contains("HEADER_LENGTH = " + (h - 2)), "README 同时承认两个 HEADER_LENGTH 值");
        // 正向对照：谓词扫的是同一个数，取一个 README 里确实出现的另一个字节数应当判 false
        assertTrue(r.contains("10 字节"), "README 关于线上 10 字节帧那段的形状变了，这条用例的对照面要重挑");
        assertFalse(r.contains("10 字节定长头"), "线上那套 10 字节帧不该被写成定长头");
    }

    // ---------------------------------------------------------------- R6 代码片段 / 用例计数

    /** 上一版 README 的示例点过名、但仓里从来不存在（或已改名）的名字。 */
    private static final List<String> PHANTOM_SYMBOLS = Collections.unmodifiableList(Arrays.asList(
            "RpcServerConfig", "RpcClientConfig", "InMemoryRegistry", "MockFilter",
            "createGenericProxy", "SmoothWeightedRR", "ConsistentHash", "AvailableCluster",
            "ConnectionManager", "HeartbeatReconnector", "ReferenceCountExchangeClient",
            "ZConfigRegistry", "ZkNamingRpcRegistry", "setInterface", "Scope\\.SINGLETON"));

    /** 词边界匹配：InMemoryRegistryService / setInterfaceClass 都是真名字，不能被当成幽灵。 */
    private static boolean carriesPhantom(String block) {
        for (String s : PHANTOM_SYMBOLS) {
            if (Pattern.compile("(?<![A-Za-z0-9_])" + s + "(?![A-Za-z0-9_])").matcher(block).find()) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("```java 片段里不出现仓里不存在的类/方法名（上一版 13 段有 9 段编译不过）")
    void codeFencesCarryNoPhantomSymbols() throws IOException {
        String r = readme();
        List<String> java = fencesOf(r, "java");
        assertTrue(java.size() >= 5, "只解析出 " + java.size() + " 段 java 片段，围栏解析没跑通");

        List<String> offenders = new ArrayList<String>();
        for (int i = 0; i < java.size(); i++) {
            if (carriesPhantom(java.get(i))) {
                offenders.add("fence#" + i);
            }
        }
        assertEquals(Collections.<String>emptyList(), offenders,
                "示例代码点了不存在的名字，用户照抄必然编译不过");

        // 正向对照 1：这些名字在正文的「旧 README 主张」表里是允许出现的，谓词只看围栏
        int mentioned = 0;
        for (String s : PHANTOM_SYMBOLS) {
            if (r.contains(s.replace("\\", ""))) {
                mentioned++;
            }
        }
        assertTrue(mentioned >= 5, "正文一个幽灵名字都没提到，说明「围栏内 0 命中」没有对照面");
        // 正向对照 2：真名字不许被误判
        assertFalse(carriesPhantom("InMemoryRegistryService s = new InMemoryRegistryService();\n"),
                "把 InMemoryRegistryService 误判成 InMemoryRegistry，谓词会在真代码上变红");
        assertFalse(carriesPhantom("ref.setInterfaceClass(UserService.class);\n"),
                "把 setInterfaceClass 误判成 setInterface，同上");
        // 正向对照 3：猎物必须被抓到
        assertTrue(carriesPhantom("RpcServerConfig cfg = new RpcServerConfig();\n"),
                "抓不到围栏里的 RpcServerConfig，放行没有意义");
    }

    @Test
    @DisplayName("README 报的测试源文件数等于磁盘实测，而且这个数把本文件也算进去了")
    void testSourceFileCountMatchesDisk() throws IOException {
        File root = repoRoot();
        List<String> files = new ArrayList<String>();
        collectTestSources(root, files);
        Collections.sort(files);

        int mine = 0;
        for (String f : files) {
            if (f.endsWith("/src/test/java/com/zifang/z/rpc/starter/docs/ReadmeContractTest.java")) {
                mine++;
            }
        }
        assertEquals(1, mine, "遍历里没有（或有多个）本文件，说明遍历根/过滤条件不对，共 " + files.size() + " 项");
        assertTrue(readme().contains(files.size() + " 个测试源文件"),
                "README 的测试源文件数与实测 " + files.size() + " 不一致");
    }

    private static void collectTestSources(File dir, List<String> sink) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            String name = k.getName();
            if (k.isDirectory()) {
                if ("target".equals(name) || "node_modules".equals(name) || ".git".equals(name)) {
                    continue;
                }
                collectTestSources(k, sink);
            } else if (name.endsWith(".java") && k.getAbsolutePath().contains("/src/test/java/")) {
                sink.add(k.getAbsolutePath());
            }
        }
    }

    /** 模块表里某一行的"里面真实存在的东西"那格；行不存在直接判红，免得谓词对着空串恒真。 */
    private static String moduleRowCell(String readme, String moduleName) {
        for (String[] row : moduleTableRows(readme)) {
            if (row[0].equals(moduleName)) {
                return row[2];
            }
        }
        throw new AssertionError("README 的模块表里没有 " + moduleName + " 这一行");
    }

    private static boolean carriesPort(String cell, int port) {
        return Pattern.compile("(?<!\\d)" + port + "(?!\\d)").matcher(cell).find();
    }

    private static List<String> names(List<String[]> rows) {
        List<String> out = new ArrayList<String>();
        for (String[] r : rows) {
            out.add(r[0]);
        }
        return out;
    }
}

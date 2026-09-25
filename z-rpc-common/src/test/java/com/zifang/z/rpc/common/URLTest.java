package com.zifang.z.rpc.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for {@link URL}: parsing, printing, parameter handling and identity semantics.
 *
 * <p>All assertions describe what the current implementation actually does. Tests named
 * {@code bug_...} pin behaviour that is wrong; the comment above each states what correct would be.
 */
@DisplayName("URL valueOf/toString/parameters")
class URLTest {

    // ---------------------------------------------------------------- valueOf: happy paths

    @Test
    void valueOf_fullUrl_populatesAllAddressParts() {
        URL url = URL.valueOf("z-rpc://10.0.0.1:20880/com.zifang.demo.DemoService?timeout=3000&weight=100");

        assertEquals("z-rpc", url.getProtocol());
        assertEquals("10.0.0.1", url.getHost());
        assertEquals(20880, url.getPort());
        assertEquals("com.zifang.demo.DemoService", url.getServiceInterface());
        assertEquals("3000", url.getParameter("timeout"));
        assertEquals("100", url.getParameter("weight"));
        assertEquals(2, url.getParameters().size());
    }

    @Test
    void valueOf_withoutProtocol_leavesProtocolNull() {
        URL url = URL.valueOf("10.0.0.1:20880/com.Foo");

        assertNull(url.getProtocol());
        assertEquals("10.0.0.1", url.getHost());
        assertEquals(20880, url.getPort());
        assertEquals("com.Foo", url.getServiceInterface());
    }

    @Test
    void valueOf_withoutPort_keepsPortZero() {
        URL url = URL.valueOf("z-rpc://myhost/com.Foo");

        assertEquals("myhost", url.getHost());
        assertEquals(0, url.getPort());
        assertEquals("com.Foo", url.getServiceInterface());
        assertEquals("myhost:0", url.getAddress());
    }

    @Test
    void valueOf_withoutPath_returnsHostPortOnly() {
        URL url = URL.valueOf("z-rpc://127.0.0.1:20880");

        assertEquals("127.0.0.1", url.getHost());
        assertEquals(20880, url.getPort());
        assertNull(url.getServiceInterface());
        assertTrue(url.getParameters().isEmpty());
    }

    @Test
    void valueOf_trailingSlashAfterPort_yieldsNullServiceInterface() {
        URL url = URL.valueOf("z-rpc://127.0.0.1:20880/");

        assertEquals("127.0.0.1", url.getHost());
        assertNull(url.getServiceInterface());
    }

    @Test
    void valueOf_null_returnsNull() {
        assertNull(URL.valueOf(null));
    }

    @Test
    void valueOf_empty_returnsNull() {
        assertNull(URL.valueOf(""));
    }

    @Test
    @DisplayName("valueOf of a blank string is accepted as a bare host")
    void valueOf_blankString_isNotTreatedAsEmpty() {
        // " " is neither null nor empty, so it becomes the host verbatim - no validation at all.
        URL url = URL.valueOf("   ");

        assertEquals("   ", url.getHost());
        assertEquals(0, url.getPort());
        assertNull(url.getProtocol());
    }

    // ---------------------------------------------------------------- valueOf: malformed input

    @Test
    void valueOf_nonNumericPort_throwsNumberFormatException() {
        assertThrows(NumberFormatException.class, () -> URL.valueOf("z-rpc://host:abc/com.Foo"));
    }

    @Test
    void valueOf_emptyPort_throwsNumberFormatException() {
        assertThrows(NumberFormatException.class, () -> URL.valueOf("z-rpc://host:/com.Foo"));
    }

    @Test
    @DisplayName("bug_ valueOf cannot parse an IPv6 host")
    void bug_valueOf_rejectsIpv6Host() {
        // Correct: strip the brackets and parse the port after the last ':'.
        assertThrows(NumberFormatException.class, () -> URL.valueOf("z-rpc://[::1]:20880/com.Foo"));
    }

    @Test
    @DisplayName("bug_ valueOf with a query but no path corrupts the port")
    void bug_valueOf_queryWithoutPath_corruptsHostAndPort() {
        // Correct: the port is 20880 and the query belongs to the parameters.
        assertThrows(NumberFormatException.class,
                () -> URL.valueOf("z-rpc://127.0.0.1:20880?interface=com.Foo"));
    }

    @Test
    @DisplayName("bug_ valueOf on '://host' keeps the separator inside the host")
    void bug_valueOf_missingProtocolName_keepsSeparatorInHost() {
        // 实测（不是"应当"）：indexOf("://")>0 不成立 ⇒ 协议段没被剥掉；
        // 随后 indexOf("/") 命中第 2 个字符 ⇒ address 只剩一个冒号，剩下的全成了 serviceInterface。
        URL url = URL.valueOf("://127.0.0.1:20880");

        assertNull(url.getProtocol());
        assertEquals(":", url.getHost());
        assertEquals(0, url.getPort());
        assertEquals("/127.0.0.1:20880", url.getServiceInterface(),
                "真正的 host:port 被塞进 serviceInterface，解析结果整体错位");
        assertEquals("::0", url.getAddress(), "getAddress() 就是 host + \":\" + port，于是分隔符被当成主机名");
    }

    @Test
    @DisplayName("bug_ valueOf of 'proto:host:port/path' splits on the first colon")
    void bug_valueOf_withoutSlashSeparator_misSplitsHost() {
        // Correct: require "://" before accepting a protocol prefix.
        assertThrows(NumberFormatException.class, () -> URL.valueOf("z-rpc:host:1/svc"));
    }

    // ---------------------------------------------------------------- parameter parsing

    @Test
    void valueOf_duplicateParams_lastOccurrenceWins() {
        URL url = URL.valueOf("z-rpc://h:1/svc?timeout=100&timeout=200");

        assertEquals("200", url.getParameter("timeout"));
        assertEquals(1, url.getParameters().size());
    }

    @Test
    void valueOf_valueContainingEquals_keepsTailIntact() {
        URL url = URL.valueOf("z-rpc://h:1/svc?expr=a=b=c");

        assertEquals("a=b=c", url.getParameter("expr"));
    }

    @Test
    @DisplayName("bug_ valueOf_value containing '&' is silently truncated")
    void bug_valueOf_valueContainingAmpersandIsTruncated() {
        // Correct: percent-encoded values must be decoded, so 'a&b' survives as one value.
        URL url = URL.valueOf("z-rpc://h:1/svc?token=a&b");

        assertEquals("a", url.getParameter("token"));
        assertNull(url.getParameter("b"));
        assertEquals(1, url.getParameters().size());
    }

    @Test
    @DisplayName("bug_ valueOf performs no percent-decoding")
    void bug_valueOf_doesNotUrlDecodeValues() {
        // Correct: 'a%26b' should decode to "a&b".
        URL url = URL.valueOf("z-rpc://h:1/svc?token=a%26b%3Dc");

        assertEquals("a%26b%3Dc", url.getParameter("token"));
    }

    @Test
    void valueOf_pairWithoutEquals_isIgnored() {
        URL url = URL.valueOf("z-rpc://h:1/svc?lonely&timeout=5");

        assertEquals(1, url.getParameters().size());
        assertEquals("5", url.getParameter("timeout"));
    }

    @Test
    void valueOf_pairWithEmptyKey_isIgnored() {
        URL url = URL.valueOf("z-rpc://h:1/svc?=novalue&timeout=5");

        assertEquals(1, url.getParameters().size());
        assertNull(url.getParameter("novalue"));
    }

    @Test
    void valueOf_emptyValue_isStoredAsEmptyString() {
        URL url = URL.valueOf("z-rpc://h:1/svc?flag=");

        assertEquals("", url.getParameter("flag"));
        // getParameter(key,def) only falls back for null, not for "".
        assertEquals("", url.getParameter("flag", "fallback"));
    }

    @Test
    @DisplayName("bug_ valueOf never populates the group/version fields from parameters")
    void bug_valueOf_doesNotPopulateGroupOrVersionFromParams() {
        // Correct: keys "group"/"version" are the canonical place to read these two fields from.
        URL url = URL.valueOf("z-rpc://h:1/com.Foo?group=pay&version=2.0.0");

        assertNull(url.getGroup());
        assertNull(url.getVersion());
        assertEquals("com.Foo", url.getServiceKey());
    }

    // ---------------------------------------------------------------- addParameter / getParameter

    @Test
    void addParameter_returnsSameInstance_forChaining() {
        URL url = new URL("z-rpc", "h", 1);

        assertSame(url, url.addParameter("k", "v"));
        assertEquals("v", url.getParameter("k"));
    }

    @Test
    void addParameter_doesNotLeakIntoAnotherParsedInstance() {
        URL first = URL.valueOf("z-rpc://h:1/svc?a=1");
        URL second = URL.valueOf("z-rpc://h:1/svc?a=1");

        first.addParameter("only", "on-first");

        assertEquals("on-first", first.getParameter("only"));
        assertNull(second.getParameter("only"));
    }

    @Test
    @DisplayName("bug_ addParameter mutates the very instance produced by valueOf")
    void bug_addParameter_isNotCopyOnWrite() {
        // Correct (Dubbo): URL is immutable and addParameter returns a new instance.
        URL parsed = URL.valueOf("z-rpc://h:1/svc?a=1");
        URL mutated = parsed.addParameter("b", "2");

        assertSame(parsed, mutated);
        assertEquals(2, parsed.getParameters().size());
    }

    @Test
    @DisplayName("bug_ getParameters exposes the live internal map")
    void bug_getParameters_returnsLiveMap() {
        // Correct: return an unmodifiable view or a defensive copy.
        URL url = URL.valueOf("z-rpc://h:1/svc?a=1");
        Map<String, String> exported = url.getParameters();

        exported.put("injected", "yes");

        assertEquals("yes", url.getParameter("injected"));
    }

    @Test
    void addParameter_nullValue_isSilentlyIgnored() {
        URL url = new URL("z-rpc", "h", 1);
        url.addParameter("k", null);

        assertNull(url.getParameter("k"));
        assertNull(url.getParameters().get("k"));
        assertTrue(url.getParameters().isEmpty());
    }

    @Test
    @DisplayName("bug_ addParameter(k,null) cannot clear a previously stored value")
    void bug_addParameterNullCannotOverwrite() {
        // Correct: either remove the key or store null, but do not keep stale data.
        URL url = new URL("z-rpc", "h", 1);
        url.addParameter("k", "v1");
        url.addParameter("k", null);

        assertEquals("v1", url.getParameter("k"));
    }

    @Test
    void getParameter_absentKeyReturnsDefault() {
        URL url = new URL("z-rpc", "h", 1);

        assertNull(url.getParameter("missing"));
        assertEquals("def", url.getParameter("missing", "def"));
    }

    @Test
    void addParameter_nullKey_isStoredAndReadable() {
        // HashMap tolerates a null key; nothing guards against it.
        URL url = new URL("z-rpc", "h", 1);
        url.addParameter(null, "v");

        assertEquals("v", url.getParameter(null));
        assertEquals(1, url.getParameters().size());
    }

    // ---------------------------------------------------------------- getServiceKey / getAddress

    @Test
    void getServiceKey_groupAndVersionPresent() {
        URL url = new URL("z-rpc", "h", 1, "com.Foo");
        url.setGroup("pay");
        url.setVersion("1.2.3");

        assertEquals("pay/com.Foo:1.2.3", url.getServiceKey());
    }

    @Test
    void getServiceKey_groupOnly() {
        URL url = new URL("z-rpc", "h", 1, "com.Foo");
        url.setGroup("pay");

        assertEquals("pay/com.Foo", url.getServiceKey());
    }

    @Test
    void getServiceKey_versionOnly() {
        URL url = new URL("z-rpc", "h", 1, "com.Foo");
        url.setVersion("1.2.3");

        assertEquals("com.Foo:1.2.3", url.getServiceKey());
    }

    @Test
    @DisplayName("getServiceKey with null group/version does NOT print 'null'")
    void getServiceKey_nullGroupAndVersionAreGuarded() {
        // The suspicion is wrong for group/version: both are null-checked. The literal "null"
        // only appears when serviceInterface itself is missing (see the next test).
        URL url = new URL("z-rpc", "h", 1, "com.Foo");

        assertEquals("com.Foo", url.getServiceKey());

        url.setGroup("");
        url.setVersion("");
        assertEquals("com.Foo", url.getServiceKey());
    }

    @Test
    @DisplayName("bug_ getServiceKey emits the literal string 'null' when serviceInterface is unset")
    void bug_getServiceKey_emitsLiteralNullForMissingInterface() {
        // Correct: return null (or throw) instead of a key that can collide with a real
        // interface literally named "null".
        URL url = new URL("z-rpc", "h", 1);

        assertEquals("null", url.getServiceKey());
    }

    @Test
    void getServiceKey_readsFieldsNotParamsSoGroupParamIsIgnored() {
        URL url = URL.valueOf("z-rpc://h:1/com.Foo");
        url.addParameter("group", "pay");

        assertEquals("com.Foo", url.getServiceKey());
    }

    @Test
    void getAddress_isHostColonPort() {
        URL url = new URL("z-rpc", "192.168.1.2", 9000);

        assertEquals("192.168.1.2:9000", url.getAddress());
    }

    @Test
    @DisplayName("bug_ getAddress prints 'null:0' for an unset host")
    void bug_getAddress_printsLiteralNullHost() {
        // Correct: fail fast or return null when host is unset.
        URL url = new URL();

        assertEquals("null:0", url.getAddress());
    }

    // ---------------------------------------------------------------- identity semantics

    @Test
    @DisplayName("bug_ URL does not override equals/hashCode, so equal URLs are unequal")
    void bug_urlHasNoValueEquality() {
        // Correct: value-based equals/hashCode like any other config carrier.
        URL a = URL.valueOf("z-rpc://h:1/com.Foo?timeout=1");
        URL b = URL.valueOf("z-rpc://h:1/com.Foo?timeout=1");

        assertFalse(a.equals(b));
        // hashCode is Object.identity, so it never agrees with a value-based equals contract.
        assertEquals(a.hashCode(), a.hashCode());
        java.util.Set<URL> set = new java.util.HashSet<>();
        set.add(a);
        set.add(b);
        assertEquals(2, set.size());
        assertEquals(a, a);
    }

    @Test
    void urlIsNotComparable() {
        URL url = new URL("z-rpc", "h", 1);

        assertFalse(url instanceof Comparable);
        assertThrows(ClassCastException.class,
                () -> ((Comparable<URL>) url).compareTo(new URL("z-rpc", "h", 2)));
    }

    @Test
    void toString_whenNoParameters_omitsQuestionMark() {
        URL url = new URL("z-rpc", "h", 1, "com.Foo");

        assertEquals("z-rpc://h:1/com.Foo", url.toString());
    }

    @Test
    void toString_omitsProtocolWhenNull() {
        URL url = new URL(null, "h", 1, "com.Foo");

        assertEquals("h:1/com.Foo", url.toString());
    }

    @Test
    @DisplayName("bug_ toString never writes group/version/weight, so they cannot round-trip")
    void bug_toStringDropsGroupVersionWeight() {
        // Correct: emit group=/version= parameters (and weight=) like toString does for parameters.
        URL url = new URL("z-rpc", "h", 1, "com.Foo");
        url.setGroup("pay");
        url.setVersion("9.9");
        url.setWeight(50);

        URL back = URL.valueOf(url.toString());

        assertEquals("pay", url.getGroup());
        assertNull(back.getGroup());
        assertNull(back.getVersion());
        assertEquals(0, back.getWeight());
    }

    @Test
    void toString_writesSingleParameterAndParsesBack() {
        URL url = new URL("z-rpc", "h", 1, "com.Foo").addParameter("timeout", "1000");

        assertEquals("z-rpc://h:1/com.Foo?timeout=1000", url.toString());

        URL back = URL.valueOf(url.toString());
        assertEquals("1000", back.getParameter("timeout"));
    }

    @Test
    void toStringAndValueOfRoundTrip_forTenHandBuiltUrls() {
        URL[] samples = new URL[]{
                new URL("z-rpc", "127.0.0.1", 20880, "com.Foo"),
                new URL("z-rpc", "10.0.0.1", 20881, "com.Foo").addParameter("timeout", "3000"),
                new URL("http", "example.com", 80, "com.bar.Baz").addParameter("retries", "2"),
                new URL("tri", "192.168.0.7", 50051, "a.b.C").addParameter("weight", "100"),
                new URL("z-rpc", "svc-host", 1, "Svc").addParameter("a", "1").addParameter("b", "2"),
                new URL("z-rpc", "svc-host", 2, "Svc").addParameter("b", "2").addParameter("a", "1"),
                new URL("custom", "host", 65535, "com.deep.pkg.SvcIf").addParameter("k", "v"),
                new URL("z-rpc", "0.0.0.0", 0, "root"),
                new URL("registry", "reg", 2181, "com.Foo").addParameter("side", "provider"),
                new URL("z-rpc", "h", 1, "com.Foo").addParameter("interface", "com.Foo"),
        };

        for (URL url : samples) {
            String printed = url.toString();
            URL parsed = URL.valueOf(printed);
            assertNotNull(parsed, "valueOf returned null for " + printed);
            assertEquals(url.getProtocol(), parsed.getProtocol(), printed);
            assertEquals(url.getHost(), parsed.getHost(), printed);
            assertEquals(url.getPort(), parsed.getPort(), printed);
            assertEquals(url.getServiceInterface(), parsed.getServiceInterface(), printed);
            assertEquals(url.getParameters(), parsed.getParameters(), printed);
            assertEquals(url.getServiceKey(), parsed.getServiceKey(), printed);
            assertEquals(url.getAddress(), parsed.getAddress(), printed);
            // Re-printing must be stable (parameter order included, since HashMap is stable
            // for an unchanged key set).
            assertEquals(printed, parsed.toString());
        }
    }

    @Test
    void toStringForParameterValuesContainingEqualsSurvivesRoundTrip() {
        URL url = new URL("z-rpc", "h", 1, "Svc").addParameter("expr", "x=1");

        assertEquals("x=1", URL.valueOf(url.toString()).getParameter("expr"));
    }

    // ---------------------------------------------------------------- ctors / bean accessors

    @Test
    void noArgConstructor_leavesEverythingAtDefaults() {
        URL url = new URL();

        assertNull(url.getProtocol());
        assertNull(url.getHost());
        assertEquals(0, url.getPort());
        assertNull(url.getServiceInterface());
        assertNull(url.getGroup());
        assertNull(url.getVersion());
        assertEquals(0, url.getWeight());
        assertNotNull(url.getParameters());
        assertTrue(url.getParameters().isEmpty());
    }

    @Test
    void threeArgConstructor_leavesServiceInterfaceNull() {
        URL url = new URL("z-rpc", "h", 2);

        assertEquals("z-rpc", url.getProtocol());
        assertEquals("h", url.getHost());
        assertEquals(2, url.getPort());
        assertNull(url.getServiceInterface());
    }

    @Test
    void settersOverwriteParsedValues() {
        URL url = URL.valueOf("z-rpc://h:1/com.Foo?a=b");

        url.setProtocol("tri");
        url.setHost("other");
        url.setPort(9);
        url.setServiceInterface("com.Bar");
        url.setWeight(7);

        assertEquals("tri", url.getProtocol());
        assertEquals("other", url.getHost());
        assertEquals(9, url.getPort());
        assertEquals("com.Bar", url.getServiceInterface());
        assertEquals(7, url.getWeight());
        assertEquals("tri://other:9/com.Bar?a=b", url.toString());
        assertEquals("b", url.getParameter("a"));
    }

    @Test
    void setParameters_replacesTheWholeMap() {
        URL url = URL.valueOf("z-rpc://h:1/com.Foo?a=b");
        Map<String, String> replacement = new HashMap<>();
        replacement.put("only", "one");

        url.setParameters(replacement);

        assertNull(url.getParameter("a"));
        assertEquals("one", url.getParameter("only"));
        assertSame(replacement, url.getParameters());
    }

    @Test
    @DisplayName("bug_ setParameters(null) leaves toString/getParameter NPE-prone")
    void bug_setParametersNullBreaksTheUrl() {
        // Correct: reject null or substitute an empty map.
        URL url = new URL("z-rpc", "h", 1, "Svc");
        url.setParameters(null);

        assertThrows(NullPointerException.class, url::toString);
        assertThrows(NullPointerException.class, () -> url.getParameter("x"));
    }

    @Test
    void javaSerializationRoundTripKeepsAllFields() throws Exception {
        URL original = URL.valueOf("z-rpc://1.2.3.4:6/com.Foo?x=y");
        original.setGroup("g");
        original.setVersion("v");
        original.setWeight(3);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bytes);
        oos.writeObject(original);
        oos.close();

        URL back = (URL) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();

        assertEquals("z-rpc", back.getProtocol());
        assertEquals("1.2.3.4", back.getHost());
        assertEquals(6, back.getPort());
        assertEquals("com.Foo", back.getServiceInterface());
        assertEquals("y", back.getParameter("x"));
        // group/version/weight are instance fields, so unlike toString they do survive.
        assertEquals("g", back.getGroup());
        assertEquals("v", back.getVersion());
        assertEquals(3, back.getWeight());
    }
}

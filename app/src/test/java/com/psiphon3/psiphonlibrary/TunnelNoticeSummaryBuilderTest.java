package com.psiphon3.psiphonlibrary;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TunnelNoticeSummaryBuilderTest {

    @Test
    public void connectingServer_includesProtocolAndCandidate() {
        String line = "{\"noticeType\":\"ConnectingServer\",\"data\":{"
                + "\"diagnosticID\":\"abc\","
                + "\"region\":\"DE\","
                + "\"protocol\":\"OSSH\","
                + "\"candidateNumber\":3}}";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("ConnectingServer"));
        assertTrue(s.contains("protocol=OSSH"));
        assertTrue(s.contains("candidate#=3"));
    }

    @Test
    public void candidateNumber_canBeDouble() {
        String line = "{\"noticeType\":\"ConnectingServer\",\"data\":{\"protocol\":\"X\",\"candidateNumber\":4.0}}";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("candidate#="));
    }

    @Test
    public void skipsBytesTransferred() {
        String line = "{\"noticeType\":\"BytesTransferred\",\"data\":{\"x\":1}}";
        assertNull(TunnelNoticeSummaryBuilder.buildStatusLine(line));
    }

    @Test
    public void info_includesMessage() {
        String line = "{\"noticeType\":\"Info\",\"data\":{\"message\":\"hello world\"}}";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("hello world"));
    }

    @Test
    public void fallbackRaw_whenJsonMalformedButHasNoticeType() {
        String line = "{broken json \"noticeType\":\"Info\",\"data\":{";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNull(s);
        String f = TunnelNoticeSummaryBuilder.buildFallbackRawLine(line);
        assertNotNull(f);
        assertTrue(f.contains("Info"));
    }

    @Test
    public void candidateServers_hasCounts() {
        String line = "{\"noticeType\":\"CandidateServers\",\"data\":{"
                + "\"region\":\"US\","
                + "\"initialCount\":10,"
                + "\"count\":5,"
                + "\"duration\":\"1s\""
                + "}}";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("CandidateServers"));
        assertTrue(s.contains("count=5"));
    }

    // --- Prefix-format tests (PsiphonTunnel delivers "NoticeType: {data}" strings) ---

    @Test
    public void prefixFormat_connectingServer_includesProtocol() {
        String line = "ConnectingServer: {\"protocol\":\"OSSH\",\"region\":\"DE\",\"candidateNumber\":2}";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("ConnectingServer"));
        assertTrue(s.contains("protocol=OSSH"));
        assertTrue(s.contains("candidate#=2"));
    }

    @Test
    public void prefixFormat_info_plainText() {
        String line = "Info: beast mode active (workers: 8)";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("Info"));
        assertTrue(s.contains("beast mode active"));
    }

    @Test
    public void prefixFormat_info_jsonData() {
        String line = "Info: {\"message\":\"tunnel connected\",\"protocol\":\"OSSH\"}";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("tunnel connected"));
    }

    @Test
    public void prefixFormat_skipBytesTransferred() {
        String line = "BytesTransferred: {\"sent\":1024,\"received\":2048}";
        assertNull(TunnelNoticeSummaryBuilder.buildStatusLine(line));
    }

    @Test
    public void prefixFormat_extractNoticeType() {
        String type = TunnelNoticeSummaryBuilder.extractNoticeTypeQuick("ConnectingServer: {\"x\":1}");
        assertTrue("ConnectingServer".equals(type));
    }
}

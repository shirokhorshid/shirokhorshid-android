package com.psiphon3.psiphonlibrary;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TunnelNoticeStatusEmitterTest {

    @Test
    public void buildStatusLine_connectingServer() {
        String line = "{\"noticeType\":\"ConnectingServer\",\"data\":{"
                + "\"protocol\":\"OSSH\","
                + "\"candidateNumber\":2}}";
        String s = TunnelNoticeSummaryBuilder.buildStatusLine(line);
        assertNotNull(s);
        assertTrue(s.contains("ConnectingServer"));
    }

    @Test
    public void fallbackRaw_containsNoticeType() {
        String malformed = "{broken \"noticeType\":\"Alert\"";
        String s = TunnelNoticeSummaryBuilder.buildFallbackRawLine(malformed);
        assertNotNull(s);
        assertTrue(s.contains("Alert"));
    }
}

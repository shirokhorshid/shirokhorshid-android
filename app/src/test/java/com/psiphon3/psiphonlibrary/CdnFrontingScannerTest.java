package com.psiphon3.psiphonlibrary;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CdnFrontingScannerTest {
    @Test
    public void parseCandidatesExtractsNoisyIpsCidrsRangesAndDedupes() {
        String input =
                "community post: 23.72.0.1, repeated 23.72.0.1\n" +
                "2.16.0.0/30\n" +
                "2.16.0.10-2.16.0.12 # ignored 1.1.1.1\n" +
                "invalid 999.1.1.1 and words";

        List<String> parsed = CdnFrontingScanner.parseCandidates(input, 32);

        assertEquals(Arrays.asList(
                "23.72.0.1",
                "2.16.0.0",
                "2.16.0.1",
                "2.16.0.2",
                "2.16.0.3",
                "2.16.0.10",
                "2.16.0.11",
                "2.16.0.12"), parsed);
    }

    @Test
    public void parseCandidatesEnforcesExpansionLimit() {
        try {
            CdnFrontingScanner.parseCandidates("2.16.0.0/16", 128);
            fail("Expected expansion limit to reject the input");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void selectTopIpsReturnsOnlySuccessfulIpsSortedByTlsThenTcpLatency() {
        List<CdnFrontingScanner.ScanResult> results = Arrays.asList(
                CdnFrontingScanner.ScanResult.failed("2.16.0.9", 12, "tcp-error"),
                CdnFrontingScanner.ScanResult.success("2.16.0.2", 18, 70, 2, 2),
                CdnFrontingScanner.ScanResult.success("2.16.0.1", 16, 30, 2, 2),
                CdnFrontingScanner.ScanResult.success("2.16.0.3", 15, 30, 2, 2));

        List<String> selected = CdnFrontingScanner.selectTopIps(results, 2);

        assertEquals(Arrays.asList("2.16.0.3", "2.16.0.1"), selected);
    }

    @Test
    public void buildProbeConfigUsesCandidateAsSniWhenNoCustomSniIsConfigured() {
        CdnFrontingScanner.ProbeConfig config =
                CdnFrontingScanner.ProbeConfig.forCandidate("92.123.102.43", "", 3000, 2);

        assertEquals("92.123.102.43", config.sniServerName);
        assertEquals("92.123.102.43", config.httpHostHeader);
        assertEquals(3000, config.timeoutMs);
        assertEquals(2, config.attempts);
    }

    @Test
    public void buildProbeConfigUsesCustomSniForAllCandidateIps() {
        CdnFrontingScanner.ProbeConfig config =
                CdnFrontingScanner.ProbeConfig.forCandidate("92.123.102.43", "a248.e.akamai.net", 3000, 2);

        assertEquals("a248.e.akamai.net", config.sniServerName);
        assertEquals("a248.e.akamai.net", config.httpHostHeader);
    }
}

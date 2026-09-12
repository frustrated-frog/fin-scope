package com.finscope.service.marketpulse;

import com.finscope.common.exception.BusinessException;
import com.finscope.domain.marketpulse.ResearchMemberResult;
import com.finscope.rpc.marketpulse.PythonResearchMemberSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyResearchServiceTest {
    @Test
    void rejectsNonStocksBeforeAnyRemoteCall() {
        var service = new DailyResearchService();
        var source = mock(PythonResearchMemberSource.class);
        ReflectionTestUtils.setField(service, "memberSource", source);
        for (String code : new String[]{"399001.SZ", "000300.SH", "510300.SH", "600519", "600519.SH?refresh=true"}) {
            assertThrows(BusinessException.class, () -> service.ensureMember(LocalDate.of(2026, 9, 11), code));
        }
        verifyNoInteractions(source);
        var expected = new ResearchMemberResult();
        when(source.ensure(LocalDate.of(2026, 9, 11), "600519.SH")).thenReturn(expected);
        assertSame(expected, service.ensureMember(LocalDate.of(2026, 9, 11), "600519.SH"));
    }
}

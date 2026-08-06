package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quote.PythonDailyBarClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自选标的日 K 线读取，把上游 Python 服务的失败翻译为明确的业务错误。
 */
@Service
public class WatchlistKlineService {
    private final PythonDailyBarClient dailyBarClient;

    @Autowired
    public WatchlistKlineService(PythonDailyBarClient dailyBarClient) {
        this.dailyBarClient = dailyBarClient;
    }

    /**
     * 获取标的最新的 {@code limit} 根日 K 线。
     *
     * @param code  六位证券代码。
     * @param limit 请求根数。
     * @return 按交易日升序排列的日线记录。
     */
    public List<DailyBarPoint> dailyBars(String code, int limit) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "标的代码格式不合法");
        }
        try {
            return dailyBarClient.fetchDailyBars(code, limit);
        } catch (ProviderContractException error) {
            if (Boolean.TRUE.equals(error.isRetryable())) {
                throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE, "行情数据暂不可用，请稍后重试");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_RESPONSE_INVALID, "行情数据返回异常");
        }
    }
}

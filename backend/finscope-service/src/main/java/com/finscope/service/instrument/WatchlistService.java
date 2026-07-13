package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 自选面板服务：添加/删除标的、按需拉取行情组装面板视图。
 * 标的库按需拉取：添加时若行情源能取到名称则落库，取不到也允许存但标记。
 */
@Service
@Slf4j
public class WatchlistService {
    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("\\d{6}");
    private static final Pattern SECTOR_CODE = Pattern.compile("BK\\d{4}", Pattern.CASE_INSENSITIVE);
    @Resource
    private InstrumentRepository instrumentRepository;
    @Resource
    private WatchlistRepository watchlistRepository;
    @Resource
    private QuoteService quoteService;
    @Resource
    private AttributionRepository attributionRepository;

    /** 添加标的到自选（按需拉取标的名称）。 */
    public WatchlistItem add(String code, String type, String groupName) {
        String normalizedCode = normalizeCode(code);
        String normalizedType = normalizeType(type);
        if (StringUtils.isBlank(normalizedCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标的代码不能为空");
        }
        validateCode(normalizedCode, normalizedType);

        Instrument instrument = instrumentRepository.findByCodeAndType(normalizedCode, normalizedType)
                .orElseGet(() -> createInstrument(normalizedCode, normalizedType));

        if (watchlistRepository.existsByInstrumentId(instrument.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该标的已在自选列表中");
        }

        WatchlistItem item = new WatchlistItem();
        item.setInstrumentId(instrument.getId());
        item.setGroupName(StringUtils.isBlank(groupName) ? null : groupName.trim());
        item.setSortOrder(0);
        WatchlistItem saved = watchlistRepository.save(item);
        // 回填展示字段
        saved.setCode(instrument.getCode());
        saved.setType(instrument.getType());
        saved.setName(instrument.getName());
        saved.setMarket(instrument.getMarket());
        saved.setSectorCode(instrument.getSectorCode());
        log.info("自选添加成功 code={} type={} instrumentId={}", normalizedCode, normalizedType, instrument.getId());
        return saved;
    }

    /** 列表：带实时行情，按标的类型批量拉取。 */
    public List<WatchlistItemView> listWithQuotes() {
        return listWithQuotes(false);
    }

    public List<WatchlistItemView> listWithQuotes(boolean forceRefresh) {
        List<WatchlistItem> items = watchlistRepository.findAll();
        if (items.isEmpty()) {
            return new ArrayList<>();
        }
        // 按类型分组，批量拉行情
        Map<String, List<String>> codesByType = new LinkedHashMap<>();
        for (WatchlistItem item : items) {
            codesByType.computeIfAbsent(item.getType(), key -> new ArrayList<>()).add(item.getCode());
        }
        Map<String, Quote> quoteByKey = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : codesByType.entrySet()) {
            List<Quote> quotes = quoteService.fetch(entry.getKey(), entry.getValue(), forceRefresh);
            for (Quote quote : quotes) {
                quoteByKey.put(quoteKey(entry.getKey(), quote.getInstrumentCode()), quote);
            }
        }
        Map<String, AttributionRepository.AttributionSummaryView> summaryByKey =
                attributionRepository.findLatestCompletedSummaryViews();
        List<WatchlistItemView> views = new ArrayList<>();
        for (WatchlistItem item : items) {
            Quote quote = quoteByKey.get(quoteKey(item.getType(), item.getCode()));
            views.add(new WatchlistItemView(item, quote, summaryByKey.get(quoteKey(item.getType(), item.getCode()))));
        }
        return views;
    }

    public void remove(Long id) {
        watchlistRepository.delete(id);
    }

    /** 修改自选标的所属分组（空值表示移出分组，归入默认组）。 */
    public void updateGroup(Long id, String groupName) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "自选条目 id 不能为空");
        }
        if (!watchlistRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "自选条目不存在");
        }
        String normalized = StringUtils.isBlank(groupName) ? null : groupName.trim();
        watchlistRepository.updateGroup(id, normalized);
        log.info("自选分组更新 id={} group={}", id, normalized);
    }

    private Instrument createInstrument(String code, String type) {
        Instrument instrument = new Instrument();
        instrument.setCode(code);
        instrument.setType(type);
        instrument.setName(resolveName(code, type));
        instrument.setMarket(guessMarket(code, type));
        instrument.setAliases(code);
        return instrumentRepository.save(instrument);
    }

    /** 通过行情源尝试解析标的名称，取不到则用代码占位。 */
    private String resolveName(String code, String type) {
        try {
            List<String> single = new ArrayList<>();
            single.add(code);
            List<Quote> quotes = quoteService.fetch(type, single);
            if (!quotes.isEmpty() && StringUtils.isNotBlank(quotes.get(0).getName())) {
                return quotes.get(0).getName();
            }
        } catch (Exception ex) {
            log.warn("解析标的名称失败 code={} type={} message={}", code, type, ex.getMessage());
        }
        return code;
    }

    private String guessMarket(String code, String type) {
        if (!"STOCK".equals(type)) {
            return null;
        }
        if (code.startsWith("6")) {
            return "SH";
        }
        if (code.startsWith("0") || code.startsWith("3")) {
            return "SZ";
        }
        return null;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("STOCK".equals(normalized) || "FUND".equals(normalized) || "SECTOR".equals(normalized)) {
            return normalized;
        }
        return "STOCK";
    }

    private String quoteKey(String type, String code) {
        return type + ":" + code;
    }

    private void validateCode(String code, String type) {
        boolean valid = "SECTOR".equals(type) ? SECTOR_CODE.matcher(code).matches() : SIX_DIGIT_CODE.matcher(code).matches();
        if (!valid) {
            String example = "SECTOR".equals(type) ? "BK0477" : "600519";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标的代码格式不正确，示例：" + example);
        }
    }
}

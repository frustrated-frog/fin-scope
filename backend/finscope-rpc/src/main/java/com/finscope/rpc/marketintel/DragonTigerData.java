package com.finscope.rpc.marketintel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finscope.domain.marketintel.DragonTigerRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider 规范化后的龙虎榜记录及数据质量告警。
 */
@Data
public final class DragonTigerData {
    private final List<DragonTigerRecord> records;
    private final List<String> warnings;

    @JsonCreator
    public DragonTigerData(@JsonProperty("records") List<DragonTigerRecord> records, @JsonProperty("warnings") List<String> warnings) {
        this.records = immutable(records);
        this.warnings = immutable(warnings);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(
                values == null ? Collections.<T>emptyList() : values));
    }
}

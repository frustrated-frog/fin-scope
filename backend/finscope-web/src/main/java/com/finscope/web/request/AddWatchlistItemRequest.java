package com.finscope.web.request;

import lombok.Data;

@Data
public class AddWatchlistItemRequest {
    /** 标的代码，如 600519 / 000001 */
    private String code;
    /** 标的类型：STOCK | FUND。板块使用 /api/sector-market/follows。 */
    private String type;
    /** 分组名称，可空 */
    private String groupName;
}

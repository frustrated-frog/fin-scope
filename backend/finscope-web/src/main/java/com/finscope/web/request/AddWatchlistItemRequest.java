package com.finscope.web.request;

public class AddWatchlistItemRequest {
    /** 标的代码，如 600519 / 000001 */
    private String code;
    /** 标的类型：STOCK | FUND。板块使用 /api/sector-market/follows。 */
    private String type;
    /** 分组名称，可空 */
    private String groupName;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}

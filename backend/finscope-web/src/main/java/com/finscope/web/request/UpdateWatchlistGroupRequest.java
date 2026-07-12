package com.finscope.web.request;

/**
 * 修改自选标的分组请求。
 */
public class UpdateWatchlistGroupRequest {
    /** 目标分组名称，空/null 表示移出分组（归入默认组） */
    private String groupName;

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
package com.finscope.web.response;

import com.finscope.domain.instrument.WatchlistItem;
import lombok.Data;

@Data
public class WatchlistMemberResponse {
    private Long id;
    private String code;
    private String type;
    private String name;
    private String sectorCode;

    public static WatchlistMemberResponse of(WatchlistItem item) {
        var response = new WatchlistMemberResponse();
        response.setId(item.getId());
        response.setCode(item.getCode());
        response.setType(item.getType());
        response.setName(item.getName());
        response.setSectorCode(item.getSectorCode());
        return response;
    }
}

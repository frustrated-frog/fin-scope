package com.finscope.web.response;

import com.finscope.domain.instrument.WatchlistItem;
import lombok.Data;

@Data
public class WatchlistFocusResponse {
    private Long watchlistId;
    private String code;
    private String type;
    private String name;
    private String sectorCode;
    private String reason;
    private String nextWatch;
    private String direction;

    public static WatchlistFocusResponse of(WatchlistItem item) {
        var response = new WatchlistFocusResponse();
        response.setWatchlistId(item.getId());
        response.setCode(item.getCode());
        response.setType(item.getType());
        response.setName(item.getName());
        response.setSectorCode(item.getSectorCode());
        response.setReason(item.getReason());
        response.setNextWatch(item.getNextWatch());
        response.setDirection(item.getDirection());
        return response;
    }
}

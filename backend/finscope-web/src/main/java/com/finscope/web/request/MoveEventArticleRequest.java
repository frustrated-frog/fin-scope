package com.finscope.web.request;

import lombok.Data;

@Data
public class MoveEventArticleRequest {
    private Long targetEventId;
    private Boolean createNewEvent;
}

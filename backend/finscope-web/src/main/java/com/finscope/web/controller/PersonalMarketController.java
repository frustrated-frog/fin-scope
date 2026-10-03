package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.marketpulse.PersonalMarketService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.PersonalMarketChangeResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market-pulse/personal")
public class PersonalMarketController {
    @Resource
    private PersonalMarketService service;

    @GetMapping
    public ApiResponse<List<PersonalMarketChangeResponse>> changes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponses.success(service.changes(businessDate).stream()
                .map(PersonalMarketChangeResponse::of).collect(Collectors.toList()));
    }
}

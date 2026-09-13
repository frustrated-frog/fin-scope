package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.desktopths.ThsSnapshot;
import com.finscope.service.desktopths.ThsDesktopService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/desktop-ths")
public class ThsDesktopController {
    @Resource
    private ThsDesktopService service;

    @PostMapping("/capture")
    public ApiResponse<ThsSnapshot> capture() {
        return ApiResponses.success(service.capture());
    }
}

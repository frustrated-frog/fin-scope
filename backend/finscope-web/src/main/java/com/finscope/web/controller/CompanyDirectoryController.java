package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.company.CompanySearchResult;
import com.finscope.service.company.GlobalCompanySearchService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyDirectoryController {
    private final GlobalCompanySearchService search;

    public CompanyDirectoryController(GlobalCompanySearchService search) {
        this.search = search;
    }

    @GetMapping("/search")
    public ApiResponse<List<CompanySearchResult>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "8") int limit) {
        if (query == null || query.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请输入公司名称或股票代码");
        }
        if (limit < 1 || limit > 20) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "搜索结果数量必须在 1–20 之间");
        }
        return ApiResponses.success(search.search(query.trim(), limit));
    }
}

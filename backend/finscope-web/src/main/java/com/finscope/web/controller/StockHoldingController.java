package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.service.strategy.holding.StockAccountService;
import com.finscope.service.strategy.holding.StockTransactionService;
import com.finscope.web.request.strategy.CreateStockTransactionRequest;
import com.finscope.web.request.strategy.ReverseStockTransactionRequest;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.strategy.StockAccountResponse;
import com.finscope.web.response.strategy.StockTransactionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/strategy")
public class StockHoldingController {
    @Resource
    private StockTransactionService transactions;
    @Resource
    private StockAccountService accounts;

    @GetMapping("/stock-account")
    public ApiResponse<StockAccountResponse> account() {
        return ApiResponses.success(StockAccountResponse.of(accounts.snapshot()));
    }

    @GetMapping("/stock-transactions")
    public ApiResponse<List<StockTransactionResponse>> transactions(
            @RequestParam(defaultValue = "100") int limit) {
        List<StockTransactionResponse> responses = new ArrayList<StockTransactionResponse>();
        for (StockTransaction value : transactions.list(limit)) {
            responses.add(StockTransactionResponse.of(value));
        }
        return ApiResponses.success(responses);
    }

    @PostMapping("/stock-transactions")
    public ApiResponse<StockTransactionResponse> create(
            @RequestBody CreateStockTransactionRequest request) {
        return ApiResponses.success(StockTransactionResponse.of(
                transactions.create(request.getCode(), request.toTransaction())));
    }

    @PostMapping("/stock-transactions/{id}/reverse")
    public ApiResponse<StockTransactionResponse> reverse(
            @PathVariable Long id, @RequestBody ReverseStockTransactionRequest request) {
        return ApiResponses.success(StockTransactionResponse.of(transactions.reverse(
                id, request.getClientRequestId(), request.getTradeDate(), request.getNote())));
    }
}

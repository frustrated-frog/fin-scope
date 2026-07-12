package com.finscope.web.request.strategy;
import lombok.Data;
@Data public class CreateStrategyStockThesisRequest { private String code; private String thesis; private String buyConditions; private String invalidationConditions; private String watchFocus; private String note; }

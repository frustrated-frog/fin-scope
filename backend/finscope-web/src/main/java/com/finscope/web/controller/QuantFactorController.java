package com.finscope.web.controller;

import com.finscope.domain.quant.factor.FactorDefinition;
import com.finscope.service.quant.factor.FactorRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/quant/factors")
public class QuantFactorController {
    @Resource private FactorRegistry registry;
    @GetMapping public List<FactorDefinition> list() { return registry.list(); }
}

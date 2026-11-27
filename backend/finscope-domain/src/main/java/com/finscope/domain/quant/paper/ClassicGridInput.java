package com.finscope.domain.quant.paper;

import java.util.List;

/** 同一份冻结回测上的参数组合。 */
public record ClassicGridInput(List<String> frequencies, List<String> allocations, List<Integer> holdings) {
}

package com.finscope.rpc.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import lombok.Value;

import java.util.List;

@Value
public class CapitalReplayData {
    List<CapitalFlowPoint> prices;
    List<CapitalFlowPoint> flows;
    List<String> warnings;
}

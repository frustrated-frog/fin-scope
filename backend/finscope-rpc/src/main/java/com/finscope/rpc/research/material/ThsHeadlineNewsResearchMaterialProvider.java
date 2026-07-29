package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;

@Component
public class ThsHeadlineNewsResearchMaterialProvider extends AbstractThsNewsResearchMaterialProvider {
    private static final URI ENDPOINT = URI.create("https://stock.10jqka.com.cn/thsgd/ywjh.js");

    @Autowired
    public ThsHeadlineNewsResearchMaterialProvider(AcquisitionRuntime runtime, ObjectMapper json) {
        this(runtime, json, Clock.systemDefaultZone());
    }

    ThsHeadlineNewsResearchMaterialProvider(AcquisitionRuntime runtime, ObjectMapper json, Clock clock) {
        super(runtime, json, clock, ENDPOINT, "THS_NEWS_DIGEST", 20);
    }
}

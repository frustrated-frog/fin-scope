package com.finscope.service.news;

import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.llm.NewsAiLunaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 资讯工作台的模型能力独立开关；默认仅运行确定性处理。 */
@Service
public class NewsWorkbenchCapabilities {
    @Autowired
    private LlmChatClient llm;
    @Autowired
    private NewsAiLunaClient luna;
    @Value("${finscope.news-workbench.model-enabled:false}")
    private boolean modelEnabled;

    @Value("${finscope.news-workbench.automatic-model-enabled:false}")
    private boolean automaticModelEnabled;

    public boolean isInterpretationEnabled() {
        return modelEnabled && luna.isConfigured();
    }

    public boolean isAutomaticInterpretationEnabled() {
        return automaticModelEnabled && isInterpretationEnabled();
    }

    public boolean isAutomaticModelEnabled() {
        return automaticModelEnabled && isModelEnabled();
    }

    public boolean isModelEnabled() {
        return modelEnabled && llm.isConfigured();
    }
}

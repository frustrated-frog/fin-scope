package com.finscope.service.news;

import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 资讯工作台的模型能力独立开关；默认仅运行确定性处理。 */
@Service
public class NewsWorkbenchCapabilities {
    @Autowired
    private LlmChatClient llm;
    @Value("${finscope.news-workbench.model-enabled:false}")
    private boolean modelEnabled;

    public boolean isModelEnabled() {
        return modelEnabled && llm.isConfigured();
    }
}

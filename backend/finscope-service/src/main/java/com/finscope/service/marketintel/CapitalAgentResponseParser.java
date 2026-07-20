package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** 从模型说明文字或 Markdown 代码块中提取第一个完整 JSON 对象。 */
@Component
public class CapitalAgentResponseParser {
    private final ObjectMapper json;

    public CapitalAgentResponseParser(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode parse(String output) throws Exception {
        if (output == null) throw new IllegalArgumentException("模型返回内容为空");
        int start = output.indexOf('{');
        if (start < 0) throw new IllegalArgumentException("模型响应中未找到 JSON 对象");
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < output.length(); i++) {
            char value = output.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
                continue;
            }
            if (value == '"') quoted = true;
            else if (value == '{') depth++;
            else if (value == '}' && --depth == 0) return json.readTree(output.substring(start, i + 1));
        }
        throw new IllegalArgumentException("模型响应中的 JSON 对象不完整");
    }
}

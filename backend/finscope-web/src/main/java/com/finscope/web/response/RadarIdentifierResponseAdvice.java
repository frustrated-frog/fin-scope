package com.finscope.web.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/** 在 HTTP 边界保护雷达长整数 ID，同时兼容已生成的数字型 JSON 缓存。 */
@RestControllerAdvice
public class RadarIdentifierResponseAdvice implements ResponseBodyAdvice<Object> {
    @Resource
    private ObjectMapper mapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                   Class<? extends HttpMessageConverter<?>> converterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        boolean radar = path.equals("/api/research-radar") || path.startsWith("/api/research-radar/")
                || path.equals("/api/dashboard/hotspots");
        if (body == null || !radar) {
            return body;
        }
        JsonNode tree = mapper.valueToTree(body);
        stringifyIdentifiers(tree);
        return tree;
    }

    private void stringifyIdentifiers(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = object.get(name);
                if (("id".equals(name) || name.endsWith("Id")) && value.isIntegralNumber()) {
                    object.put(name, value.asText());
                } else {
                    stringifyIdentifiers(value);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) {
                stringifyIdentifiers(value);
            }
        }
    }
}

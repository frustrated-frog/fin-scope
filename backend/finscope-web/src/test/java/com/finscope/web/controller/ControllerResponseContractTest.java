package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "finscope.data-root=target/test-data/controller-response-contract",
        "spring.datasource.url=jdbc:sqlite:target/test-data/controller-response-contract/finance.db"
})
class ControllerResponseContractTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void everyJsonEndpointDeclaresTheUnifiedResponseType() {
        List<String> violations = new ArrayList<String>();
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);

        for (Object controller : controllers.values()) {
            Class<?> controllerType = AopUtils.getTargetClass(controller);
            if (!controllerType.getPackage().getName().startsWith("com.finscope.web.controller")) {
                continue;
            }
            for (Method method : controllerType.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                if (!declaresUnifiedResponse(method)) {
                    violations.add(controllerType.getSimpleName() + "#" + method.getName()
                            + " -> " + method.getGenericReturnType().getTypeName());
                }
            }
        }

        assertTrue(violations.isEmpty(), "以下接口仍在直接返回裸实体：\n" + String.join("\n", violations));
    }

    private boolean declaresUnifiedResponse(Method method) {
        Class<?> rawType = method.getReturnType();
        if (declaresNoContent(method)) {
            return isResponseEntityOf(method, Void.class);
        }
        if (ApiResponse.class.isAssignableFrom(rawType)) {
            return method.getGenericReturnType() instanceof ParameterizedType;
        }
        if (SseEmitter.class.isAssignableFrom(rawType)) {
            return true;
        }
        if (!ResponseEntity.class.isAssignableFrom(rawType)) {
            return false;
        }
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) {
            return false;
        }
        Type bodyType = ((ParameterizedType) returnType).getActualTypeArguments()[0];
        if (bodyType == Void.class) {
            return true;
        }
        if (bodyType instanceof ParameterizedType) {
            return ((ParameterizedType) bodyType).getRawType() == ApiResponse.class;
        }
        return false;
    }

    private boolean declaresNoContent(Method method) {
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
        return responseStatus != null
                && (responseStatus.code() == HttpStatus.NO_CONTENT
                || responseStatus.value() == HttpStatus.NO_CONTENT);
    }

    private boolean isResponseEntityOf(Method method, Class<?> bodyClass) {
        if (!ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
            return false;
        }
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) {
            return false;
        }
        return ((ParameterizedType) returnType).getActualTypeArguments()[0] == bodyClass;
    }
}

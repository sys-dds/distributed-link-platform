package com.linkplatform.api.runtime;

import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RuntimeModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnRuntimeModes.class.getName());
        if (attributes == null) {
            return true;
        }

        RuntimeMode configuredMode = Binder.get(context.getEnvironment())
                .bind("link-platform.runtime.mode", Bindable.of(RuntimeMode.class))
                .orElse(RuntimeMode.ALL);
        RuntimeMode[] allowedModes = (RuntimeMode[]) attributes.get("value");
        return Arrays.asList(allowedModes).contains(configuredMode);
    }
}

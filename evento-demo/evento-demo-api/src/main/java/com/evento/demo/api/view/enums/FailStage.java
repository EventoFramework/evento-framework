package com.evento.demo.api.view.enums;

import com.evento.common.modeling.messaging.payload.View;

public enum FailStage implements View {

    INVOKER,
    GATEWAY,
    BEFORE_HANDLING,
    HANDLING,
    AFTER_HANDLING,
    AFTER_HANDLING_EXCEPTION,
}

package com.evento.demo.web.domain.web;

import com.evento.application.EventoBundle;
import com.evento.demo.api.view.enums.FailStage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/util")
public class UtilController {

    private final UtilInvoker utilInvoker;

    public UtilController(EventoBundle eventoBundle) {
        utilInvoker = eventoBundle.getInvoker(UtilInvoker.class);
    }

    @GetMapping("/send")
    public CompletableFuture<?> send(FailStage failStage) {
        return utilInvoker.failSend(failStage);
    }

    @GetMapping("/send-wait")
    public void sendAndWait(FailStage failStage) throws InterruptedException {
        utilInvoker.failSendAndWait(failStage);
    }

    @GetMapping("/send-event")
    public CompletableFuture<?> sendEvent(int failures, FailStage failStage) {
        return utilInvoker.failEventSend(failures,failStage);
    }
}

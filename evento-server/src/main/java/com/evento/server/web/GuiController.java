package com.evento.server.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class GuiController {
    @GetMapping("/")
    public RedirectView index() {
        return new RedirectView("dashboard");
    }
}

package com.localstudio.downloadstudio.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping(value = {"/", "/activity"})
    public String index() {
        return "forward:/index.html";
    }
}

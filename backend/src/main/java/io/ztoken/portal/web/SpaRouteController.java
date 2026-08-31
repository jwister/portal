package io.ztoken.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaRouteController {

    @GetMapping({"/", "/models", "/purchase", "/console", "/console/**", "/sign-in", "/sign-up", "/oauth/{provider}"})
    public String index() {
        return "forward:/index.html";
    }
}

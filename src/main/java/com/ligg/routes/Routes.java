package com.ligg.routes;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author Ligg
 * @create_time 2025/12/23 12:42
 * @update_time 2025/12/23 12:42
 **/
@Controller
public class Routes {

    @GetMapping("/no_session")
    public String noSession() {
        return "no_session";
    }

    @GetMapping("/oauth-success")
    public String oauthSuccess() {
        return "oauth-success";
    }

    @GetMapping("/success")
    public String Success() {
        return "success";
    }
}

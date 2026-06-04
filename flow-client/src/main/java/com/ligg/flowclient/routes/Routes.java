/**
 * @author Ligg
 * @create_time 2025/12/23 12:42
 * @update_time 2025/12/23 12:42
 **/
package com.ligg.flowclient.routes;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 页面路由
 */
@Controller
@RequestMapping("/api")
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

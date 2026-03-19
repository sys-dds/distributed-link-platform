package com.linkplatform.api.system.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemPingController {

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("ok", "link-platform-api");
    }
}

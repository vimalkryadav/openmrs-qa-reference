package org.openmrs.logic.web.controller;

import java.util.Collections;
import java.util.Map;
import org.openmrs.api.context.Context;
import org.openmrs.logic.util.LogicUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class LogicInitController {
    private volatile boolean running;

    @RequestMapping({"/module/logic/init", "/module/logic/init.form"})
    public String initialize() { return "/module/logic/init"; }

    @RequestMapping({"/module/logic/status", "/module/logic/status.form"})
    @ResponseBody
    public Map<String, Boolean> initStatus() {
        return Collections.singletonMap("running", running);
    }

    @RequestMapping(value={"/module/logic/load", "/module/logic/load.form"}, method=RequestMethod.POST)
    @ResponseBody
    public Map<String, Boolean> runInit() {
        if (!Context.hasPrivilege("Manage LOGIC"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manage LOGIC privilege is required");
        running = true;
        try {
            LogicUtil.registerDefaultRules();
        } finally {
            running = false;
        }
        return initStatus();
    }
}

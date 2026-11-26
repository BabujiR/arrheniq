package com.arrheniq.masterdata;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only ICH time-point templates (URS-MD-007). */
@RestController
@RequestMapping("/api/v1/protocol-templates")
public class ProtocolTemplateController {

    private final ProtocolTemplateService service;

    public ProtocolTemplateController(ProtocolTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProtocolTemplate> templates() {
        return service.templates();
    }
}

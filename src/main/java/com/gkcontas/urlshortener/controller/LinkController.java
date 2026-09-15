package com.gkcontas.urlshortener.controller;

import com.gkcontas.urlshortener.dto.CreateLinkRequest;
import com.gkcontas.urlshortener.dto.LinkResponse;
import com.gkcontas.urlshortener.dto.LinkStatsResponse;
import com.gkcontas.urlshortener.service.LinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        LinkResponse created = linkService.create(request);
        return ResponseEntity.created(URI.create("/" + created.code())).body(created);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deactivate(@PathVariable String code) {
        linkService.deactivate(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/stats")
    public LinkStatsResponse stats(@PathVariable String code) {
        return linkService.stats(code);
    }
}

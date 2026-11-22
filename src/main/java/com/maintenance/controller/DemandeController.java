package com.maintenance.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.maintenance.model.Demande;
import com.maintenance.model.Priorite;
import com.maintenance.service.DemandeService;

/**
 * Remplace DemandeServlet (Noura).
 * TODO : quand le login (Sirine) sera prêt, remplacer les en-têtes X-User-Id / X-Role
 * par l'utilisateur connecté (session / Spring Security).
 */
@RestController
@RequestMapping("/api/demandes")
public class DemandeController {

    private final DemandeService service;

    public DemandeController(DemandeService service) {
        this.service = service;
    }

    @GetMapping
    public List<Demande> lister(@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                                @RequestHeader(value = "X-Role", defaultValue = "DEMANDEUR") String role) {
        return service.lister(userId, estResponsable(role));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Demande creer(@RequestBody DemandeRequest req,
                         @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return service.creer(req, userId);
    }

    @PutMapping("/{id}")
    public Demande modifier(@PathVariable Long id, @RequestBody DemandeRequest req,
                            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                            @RequestHeader(value = "X-Role", defaultValue = "DEMANDEUR") String role) {
        return service.modifier(id, req, userId, estResponsable(role));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id,
                          @RequestHeader(value = "X-Role", defaultValue = "DEMANDEUR") String role) {
        service.supprimer(id, estResponsable(role));
    }

    @PostMapping("/{id}/annuler")
    public Demande annuler(@PathVariable Long id,
                           @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                           @RequestHeader(value = "X-Role", defaultValue = "DEMANDEUR") String role) {
        return service.annuler(id, userId, estResponsable(role));
    }

    @PutMapping("/{id}/priorite")
    public Demande changerPriorite(@PathVariable Long id, @RequestBody Map<String, String> body,
                                   @RequestHeader(value = "X-Role", defaultValue = "DEMANDEUR") String role) {
        return service.changerPriorite(id, Priorite.valueOf(body.get("priorite")), estResponsable(role));
    }

    private boolean estResponsable(String role) {
        return "RESPONSABLE".equalsIgnoreCase(role);
    }
}

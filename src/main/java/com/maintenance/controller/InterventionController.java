package com.maintenance.controller;

import com.maintenance.dao.InterventionDAO;
import com.maintenance.exception.BusinessRuleException;
import com.maintenance.model.Intervention;
import com.maintenance.model.Utilisateur;
import com.maintenance.service.PlanificationService;
import com.maintenance.service.PlanificationService.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class InterventionController {

    public record AffectationRequest(Long idDemande, Long idTechnicien, LocalDate jour) {}
    public record ValidationRequest(LocalDate jour, List<Choix> plan) {}

    private final PlanificationService planification;
    private final InterventionDAO interventionDAO;

    public InterventionController(PlanificationService planification, InterventionDAO interventionDAO) {
        this.planification = planification;
        this.interventionDAO = interventionDAO;
    }

    /* ---------- Planification (manager) ---------- */

    @GetMapping("/api/planification/proposition")
    public Proposition proposer(@RequestParam LocalDate jour, @RequestParam String algo) {
        return switch (algo) {
            case "locale" -> planification.proposerRechercheLocale(jour);
            case "arrivee" -> planification.proposerOrdreArrivee(jour);
            default -> planification.proposerGlouton(jour);
        };
    }

    @GetMapping("/api/planification/comparaison")
    public Comparaison comparer(@RequestParam LocalDate jour) {
        return planification.comparer(jour);
    }

    @PostMapping("/api/planification/valider")
    public List<Intervention> valider(@RequestBody ValidationRequest r) {
        return planification.valider(r.jour(), r.plan());
    }

    @PostMapping("/api/planification/affecter")
    public Intervention affecter(@RequestBody AffectationRequest r) {
        return planification.affecter(r.idDemande(), r.idTechnicien(), r.jour());
    }

    /* ---------- Interventions (list + status changes) ---------- */

    @GetMapping("/api/intervention")
    @Transactional(readOnly = true)
    public List<Intervention> lister(@AuthenticationPrincipal Utilisateur acteur) {
        return switch (acteur.getRole()) {
            case RESPONSABLE -> interventionDAO.findAll();
            case TECHNICIEN -> interventionDAO.findByTechnicien(acteur);
            case DEMANDEUR -> interventionDAO.findAll().stream()
                .filter(i -> i.getDemande().getDemandeur().getIdUtilisateur()
                              .equals(acteur.getIdUtilisateur()))
                .toList();
        };
    }

    @PostMapping("/api/intervention/{id}/demarrer")
    @Transactional
    public Intervention demarrer(@PathVariable Long id, @AuthenticationPrincipal Utilisateur acteur) {
        Intervention i = trouver(id);
        i.demarrer(acteur);
        return i;
    }

    @PostMapping("/api/intervention/{id}/annuler")
    @Transactional
    public Intervention annuler(@PathVariable Long id, @AuthenticationPrincipal Utilisateur acteur) {
        Intervention i = trouver(id);
        if (acteur.getRole() == Utilisateur.Role.RESPONSABLE) i.annulerParResponsable();
        else i.annulerParDemandeur(acteur);
        return i;
    }

    private Intervention trouver(Long id) {
        return interventionDAO.findById(id)
            .orElseThrow(() -> new BusinessRuleException("Intervention " + id + " introuvable."));
    }
}

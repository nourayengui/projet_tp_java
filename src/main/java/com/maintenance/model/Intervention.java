package com.maintenance.model;

import com.maintenance.exception.BusinessRuleException;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "intervention")
public class Intervention {

    public enum StatutIntervention { ASSIGNEE, EN_COURS, TERMINEE, ANNULEE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_intervention")
    private Long idIntervention;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_demande", unique = true)
    private Demande demande;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_technicien")
    private Utilisateur technicien;

    @Column(name = "date_planifiee", nullable = false)
    private LocalDate datePlanifiee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutIntervention statut = StatutIntervention.ASSIGNEE;

    protected Intervention() { }   // required by JPA

    /**
     * Creates the assignment. Conflicts that need other data (competence,
     * indisponibilite, capacite 8 h) are checked by PlanificationService BEFORE this call.
     */
    public Intervention(Demande demande, Utilisateur technicien, LocalDate datePlanifiee) {
        if (demande.getStatut() != Demande.StatutDemande.OUVERTE) {
            throw new BusinessRuleException(
                "Changement de statut non autorisé : seule une demande ouverte peut être affectée"
                + " (statut actuel : " + demande.getStatut() + ").");
        }
        if (datePlanifiee.isBefore(demande.getDateSignalement())) {
            throw new BusinessRuleException(
                "Le jour planifié ne peut pas précéder la date de signalement.");
        }
        this.demande = demande;
        this.technicien = technicien;
        this.datePlanifiee = datePlanifiee;
        this.statut = StatutIntervention.ASSIGNEE;
        demande.setStatut(Demande.StatutDemande.ASSIGNEE);   // keep both statuses in sync
    }

    /* ---------- Status transitions ---------- */

    /** ASSIGNEE -> EN_COURS, only by the assigned technician. */
    public void demarrer(Utilisateur acteur) {
        verifierTechnicienAffecte(acteur);
        if (statut != StatutIntervention.ASSIGNEE) {
            throw new BusinessRuleException(
                "Changement de statut non autorisé : l'intervention doit être assignée"
                + " pour démarrer (statut actuel : " + statut + ").");
        }
        statut = StatutIntervention.EN_COURS;
        demande.setStatut(Demande.StatutDemande.EN_COURS);
    }

    /** EN_COURS -> TERMINEE, a complete compte rendu is mandatory. */
    public void terminer(Utilisateur acteur, CompteRendu compteRendu) {
        verifierTechnicienAffecte(acteur);
        if (statut != StatutIntervention.EN_COURS) {
            throw new BusinessRuleException(
                "Changement de statut non autorisé : l'intervention doit être en cours"
                + " pour être terminée (statut actuel : " + statut + ").");
        }
        if (compteRendu == null || compteRendu.estIncomplet()) {
            throw new BusinessRuleException(
                "Compte rendu obligatoire (cause, action réalisée, durée réelle)"
                + " pour terminer une intervention.");
        }
        statut = StatutIntervention.TERMINEE;
        demande.setStatut(Demande.StatutDemande.TERMINEE);
    }

    /** Manager: any time before TERMINEE. Requester: only ASSIGNEE (see annulerParDemandeur). */
    public void annulerParResponsable() {
        if (statut == StatutIntervention.TERMINEE || statut == StatutIntervention.ANNULEE) {
            throw new BusinessRuleException(
                "Une intervention " + statut + " ne peut pas être annulée.");
        }
        statut = StatutIntervention.ANNULEE;
        demande.setStatut(Demande.StatutDemande.ANNULEE);
    }

    public void annulerParDemandeur(Utilisateur acteur) {
        if (!demande.getDemandeur().getIdUtilisateur().equals(acteur.getIdUtilisateur())) {
            throw new BusinessRuleException("Vous ne pouvez annuler que vos propres demandes.");
        }
        if (statut != StatutIntervention.ASSIGNEE) {
            throw new BusinessRuleException(
                "Une demande au statut " + statut + " ne peut être annulée que par le responsable.");
        }
        statut = StatutIntervention.ANNULEE;
        demande.setStatut(Demande.StatutDemande.ANNULEE);
    }

    /* ---------- Planning helpers ---------- */

    /** score = poids x (1 + jours d'attente), waiting days counted up to the planned day. */
    public int calculerScore() {
        long joursAttente = ChronoUnit.DAYS.between(demande.getDateSignalement(), datePlanifiee);
        return (int) (demande.getPriorite().getPoids() * (1 + joursAttente));
    }

    public int getDureeEstimee() {
        return demande.getDureeEstimee();
    }

    private void verifierTechnicienAffecte(Utilisateur acteur) {
        if (!technicien.getIdUtilisateur().equals(acteur.getIdUtilisateur())) {
            throw new BusinessRuleException("Cette intervention ne vous est pas affectée.");
        }
    }

    /* ---------- Getters / setters ---------- */

    public Long getIdIntervention() { return idIntervention; }
    public Demande getDemande() { return demande; }
    public Utilisateur getTechnicien() { return technicien; }
    public void setTechnicien(Utilisateur technicien) { this.technicien = technicien; }  // manager edits the plan
    public LocalDate getDatePlanifiee() { return datePlanifiee; }
    public void setDatePlanifiee(LocalDate datePlanifiee) { this.datePlanifiee = datePlanifiee; }
    public StatutIntervention getStatut() { return statut; }
}

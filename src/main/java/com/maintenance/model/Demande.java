package com.maintenance.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Classe Demande (Noura).
 * Contient uniquement ce qui concerne la demande elle-même.
 * - technicien / date planifiée / date de fin  -> classe Intervention (Nourhene)
 * - cause / action / durée réelle              -> classe CompteRendu (Abderrahmen)
 */
public class Demande {

    private Long id;
    private String description;
    private Long equipementId;      // lien vers Equipement (Koussay)
    private Long demandeurId;       // lien vers Utilisateur (Sirine)

    private Categorie categorie;    // = compétence requise
    private Priorite priorite;
    private int dureeEstimee;       // 1 à 8 heures
    private Statut statut;
    private LocalDate dateSignalement;

    public Demande(String description, Long equipementId, Long demandeurId,
                   Categorie categorie, Priorite priorite,
                   int dureeEstimee, LocalDate dateSignalement) {
        verifierDuree(dureeEstimee);
        this.description = description;
        this.equipementId = equipementId;
        this.demandeurId = demandeurId;
        this.categorie = categorie;
        this.priorite = priorite;
        this.dureeEstimee = dureeEstimee;
        this.dateSignalement = (dateSignalement != null) ? dateSignalement : LocalDate.now();
        this.statut = Statut.OUVERTE;
    }

    private static void verifierDuree(int duree) {
        if (duree < 1 || duree > 8) {
            throw new IllegalArgumentException(
                "Durée estimée invalide (" + duree + " h) : elle doit être comprise entre 1 et 8 heures.");
        }
    }

    // score = poids x (1 + jours d'attente)
    public int calculerScore(LocalDate jourPlanifie) {
        long joursAttente = ChronoUnit.DAYS.between(dateSignalement, jourPlanifie);
        return (int) (priorite.getPoids() * (1 + joursAttente));
    }

    // planifiable = ouverte et signalée au plus tard ce jour-là
    public boolean estPlanifiable(LocalDate jour) {
        return statut == Statut.OUVERTE && !dateSignalement.isAfter(jour);
    }

    // OUVERTE -> ASSIGNEE (appelée quand une Intervention est créée)
    public void marquerAssignee() {
        if (statut != Statut.OUVERTE) {
            throw new IllegalStateException(
                "Changement de statut non autorisé : seule une demande ouverte peut être affectée.");
        }
        statut = Statut.ASSIGNEE;
    }

    // ASSIGNEE -> EN_COURS
    public void marquerEnCours() {
        if (statut != Statut.ASSIGNEE) {
            throw new IllegalStateException(
                "Changement de statut non autorisé : la demande n'est pas assignée.");
        }
        statut = Statut.EN_COURS;
    }

    // EN_COURS -> TERMINEE (refusé sans compte rendu)
    public void marquerTerminee(boolean compteRenduFourni) {
        if (statut != Statut.EN_COURS) {
            throw new IllegalStateException(
                "Changement de statut non autorisé : la demande n'est pas en cours.");
        }
        if (!compteRenduFourni) {
            throw new IllegalStateException(
                "Compte rendu obligatoire pour terminer l'intervention.");
        }
        statut = Statut.TERMINEE;
    }

    // demandeur : tant qu'ouverte ou assignée / responsable : tant que non terminée
    public void annuler(boolean estResponsable) {
        if (statut == Statut.TERMINEE || statut == Statut.ANNULEE) {
            throw new IllegalStateException(
                "Annulation impossible : la demande est déjà " + statut + ".");
        }
        if (!estResponsable && statut != Statut.OUVERTE && statut != Statut.ASSIGNEE) {
            throw new IllegalStateException(
                "Annulation refusée : une demande en cours ne peut être annulée que par le responsable.");
        }
        statut = Statut.ANNULEE;
    }

    // délai de résolution en jours (date de fin vient de Intervention)
    public long calculerDelaiResolution(LocalDate dateFin) {
        return ChronoUnit.DAYS.between(dateSignalement, dateFin);
    }

    public boolean estDansLeDelai(LocalDate dateFin) {
        return calculerDelaiResolution(dateFin) <= priorite.getDelaiMaxJours();
    }

    // getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getEquipementId() { return equipementId; }
    public void setEquipementId(Long equipementId) { this.equipementId = equipementId; }
    public Long getDemandeurId() { return demandeurId; }
    public Categorie getCategorie() { return categorie; }
    public void setCategorie(Categorie categorie) { this.categorie = categorie; }
    public Priorite getPriorite() { return priorite; }
    public void setPriorite(Priorite priorite) { this.priorite = priorite; }
    public int getDureeEstimee() { return dureeEstimee; }
    public void setDureeEstimee(int dureeEstimee) { verifierDuree(dureeEstimee); this.dureeEstimee = dureeEstimee; }
    public Statut getStatut() { return statut; }
    public LocalDate getDateSignalement() { return dateSignalement; }
    public void setDateSignalement(LocalDate d) { if (d != null) this.dateSignalement = d; }
}

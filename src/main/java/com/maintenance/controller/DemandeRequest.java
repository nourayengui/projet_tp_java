package com.maintenance.controller;

import java.time.LocalDate;

import com.maintenance.model.Categorie;
import com.maintenance.model.Priorite;

/** Corps JSON envoyé par le front (demandes.js) pour créer / modifier une demande. */
public record DemandeRequest(
        String description,
        Long equipementId,
        Categorie categorie,
        Priorite priorite,
        int dureeEstimee,
        LocalDate dateSignalement) {
}

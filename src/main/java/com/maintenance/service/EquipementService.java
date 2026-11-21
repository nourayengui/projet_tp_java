package com.maintenance.service;

import com.maintenance.dao.EquipementDAO;
import com.maintenance.model.Equipement;

import java.sql.SQLException;
import java.util.List;

public class EquipementService {

    private final EquipementDAO equipementDAO;

    public EquipementService() {
        this.equipementDAO = new EquipementDAO();
    }


    // Lister les équipements
    public List<Equipement> getAllEquipements() throws SQLException {
        return equipementDAO.findAll();
    }


    // Chercher un équipement
    public Equipement getEquipementById(int id) throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID équipement invalide."
            );
        }

        return equipementDAO.findById(id);
    }


    // Ajouter un équipement
    public void ajouterEquipement(
            String designation,
            String lieu
    ) throws SQLException {

        // Règle métier 1
        if (designation == null || designation.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "La désignation est obligatoire."
            );
        }

        // Règle métier 2
        if (lieu == null || lieu.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le lieu est obligatoire."
            );
        }

        designation = designation.trim();
        lieu = lieu.trim();

        // Règle métier 3 : longueur
        if (designation.length() > 100) {
            throw new IllegalArgumentException(
                    "La désignation ne doit pas dépasser 100 caractères."
            );
        }

        if (lieu.length() > 100) {
            throw new IllegalArgumentException(
                    "Le lieu ne doit pas dépasser 100 caractères."
            );
        }

        Equipement equipement = new Equipement();

        equipement.setDesignation(designation);
        equipement.setLieu(lieu);

        equipementDAO.insert(equipement);
    }


    // Modifier un équipement
    public void modifierEquipement(
            int id,
            String designation,
            String lieu
    ) throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID équipement invalide."
            );
        }

        if (designation == null || designation.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "La désignation est obligatoire."
            );
        }

        if (lieu == null || lieu.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le lieu est obligatoire."
            );
        }

        designation = designation.trim();
        lieu = lieu.trim();

        if (!equipementDAO.existsById(id)) {
            throw new IllegalArgumentException(
                    "Équipement introuvable."
            );
        }

        if (designation.length() > 100) {
            throw new IllegalArgumentException(
                    "La désignation ne doit pas dépasser 100 caractères."
            );
        }

        if (lieu.length() > 100) {
            throw new IllegalArgumentException(
                    "Le lieu ne doit pas dépasser 100 caractères."
            );
        }

        Equipement equipement =
                new Equipement(id, designation, lieu);

        boolean updated = equipementDAO.update(equipement);

        if (!updated) {
            throw new SQLException(
                    "La modification a échoué."
            );
        }
    }


    // Supprimer un équipement
    public void supprimerEquipement(int id)
            throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID équipement invalide."
            );
        }

        if (!equipementDAO.existsById(id)) {
            throw new IllegalArgumentException(
                    "Équipement introuvable."
            );
        }

        // Règle métier importante :
        // ne pas supprimer un équipement
        // qui possède déjà des demandes.
        if (equipementDAO.hasDemandes(id)) {
            throw new IllegalStateException(
                    "Impossible de supprimer cet équipement : " +
                    "il possède déjà des demandes de maintenance."
            );
        }

        boolean deleted = equipementDAO.delete(id);

        if (!deleted) {
            throw new SQLException(
                    "La suppression a échoué."
            );
        }
    }
}

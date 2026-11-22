package com.maintenance.dao;

public class UtilisateurDAO {
List<Demande> findByStatutAndDateSignalementLessThanEqual(Demande.StatutDemande statut, LocalDate jour);

}

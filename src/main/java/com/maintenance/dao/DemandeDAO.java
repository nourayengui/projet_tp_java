package com.maintenance.dao;

public class DemandeDAO {
List<Demande> findByStatutAndDateSignalementLessThanEqual(Demande.StatutDemande statut, LocalDate jour);

}

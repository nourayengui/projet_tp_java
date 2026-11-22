package com.maintenance.dao;

import com.maintenance.model.Intervention;
import com.maintenance.model.Intervention.StatutIntervention;
import com.maintenance.model.Utilisateur;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterventionDAO extends JpaRepository<Intervention, Long> {

    List<Intervention> findByTechnicien(Utilisateur technicien);

    List<Intervention> findByDatePlanifiee(LocalDate jour);

    /** Hours already used by a technician on a day (capacity check: max 8 h). */
    @Query("""
           SELECT COALESCE(SUM(i.demande.dureeEstimee), 0) FROM Intervention i
           WHERE i.technicien = :tech AND i.datePlanifiee = :jour
           AND i.statut <> com.maintenance.model.Intervention.StatutIntervention.ANNULEE
           """)
    int chargeDuJour(@Param("tech") Utilisateur tech, @Param("jour") LocalDate jour);
}

package com.maintenance.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.maintenance.controller.DemandeRequest;
import com.maintenance.model.Demande;
import com.maintenance.model.Priorite;

/**
 * Service Demande (Noura).
 * TODO : remplacer la Map en mémoire par le DAO / la base MySQL.
 */
@Service
public class DemandeService {

    private final Map<Long, Demande> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public synchronized List<Demande> lister(Long userId, boolean responsable) {
        return store.values().stream()
                .filter(d -> responsable || d.getDemandeurId().equals(userId))
                .toList();
    }

    public synchronized Demande creer(DemandeRequest r, Long demandeurId) {
        if (r.description() == null || r.description().isBlank()) {
            throw new IllegalArgumentException("La description est obligatoire.");
        }
        Demande d = new Demande(r.description().trim(), r.equipementId(), demandeurId,
                r.categorie(), r.priorite(), r.dureeEstimee(), r.dateSignalement());
        d.setId(sequence.incrementAndGet());
        store.put(d.getId(), d);
        return d;
    }

    public synchronized Demande modifier(Long id, DemandeRequest r, Long userId, boolean responsable) {
        Demande d = trouver(id);
        verifierProprietaire(d, userId, responsable);
        if (d.getStatut() != com.maintenance.model.Statut.OUVERTE) {
            throw new IllegalStateException("Modification refusée : seule une demande ouverte peut être modifiée.");
        }
        d.setDureeEstimee(r.dureeEstimee());
        d.setDescription(r.description());
        d.setEquipementId(r.equipementId());
        d.setCategorie(r.categorie());
        d.setPriorite(r.priorite());
        d.setDateSignalement(r.dateSignalement());
        return d;
    }

    public synchronized void supprimer(Long id, boolean responsable) {
        if (!responsable) {
            throw new SecurityException("Suppression réservée au responsable.");
        }
        trouver(id);
        store.remove(id);
    }

    public synchronized Demande annuler(Long id, Long userId, boolean responsable) {
        Demande d = trouver(id);
        verifierProprietaire(d, userId, responsable);
        d.annuler(responsable);
        return d;
    }

    public synchronized Demande changerPriorite(Long id, Priorite p, boolean responsable) {
        if (!responsable) {
            throw new SecurityException("Changement de priorité réservé au responsable.");
        }
        Demande d = trouver(id);
        d.setPriorite(p);
        return d;
    }

    public synchronized Demande trouver(Long id) {
        Demande d = store.get(id);
        if (d == null) {
            throw new NoSuchElementException("Demande D" + id + " introuvable.");
        }
        return d;
    }

    private void verifierProprietaire(Demande d, Long userId, boolean responsable) {
        if (!responsable && !d.getDemandeurId().equals(userId)) {
            throw new SecurityException("Accès refusé : cette demande ne vous appartient pas.");
        }
    }
}

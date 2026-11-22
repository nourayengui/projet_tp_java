package com.maintenance.service;

import com.maintenance.dao.*;
import com.maintenance.exception.BusinessRuleException;
import com.maintenance.model.Demande;
import com.maintenance.model.Intervention;
import com.maintenance.model.Utilisateur;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlanificationService {

    public static final int CAPACITE_JOURNALIERE = 8;

    /* ---------- Result objects ---------- */

    public record Affectation(Demande demande, Utilisateur technicien, int score) { }

    public record Proposition(String algorithme, LocalDate jour, List<Affectation> affectations,
                              List<Demande> nonAffectees, int valeurTotale,
                              long critiquesNonTraitees) { }

    public record Comparaison(Proposition glouton, Proposition rechercheLocale,
                              Proposition ordreArrivee) { }

    /** One line of the plan sent back by the manager when validating (after his edits). */
    public record Choix(Long idDemande, Long idTechnicien) { }

    /* ---------- Dependencies ---------- */

    private final DemandeDAO demandeDAO;
    private final UtilisateurDAO utilisateurDAO;
    private final InterventionDAO interventionDAO;
    private final IndisponibiliteDAO indisponibiliteDAO;
    private final TechnicienCompetenceDAO technicienCompetenceDAO;

    public PlanificationService(DemandeDAO demandeDAO, UtilisateurDAO utilisateurDAO,
                                InterventionDAO interventionDAO,
                                IndisponibiliteDAO indisponibiliteDAO,
                                TechnicienCompetenceDAO technicienCompetenceDAO) {
        this.demandeDAO = demandeDAO;
        this.utilisateurDAO = utilisateurDAO;
        this.interventionDAO = interventionDAO;
        this.indisponibiliteDAO = indisponibiliteDAO;
        this.technicienCompetenceDAO = technicienCompetenceDAO;
    }

    /* =====================================================================
     *  1. PROPOSALS (nothing is saved in the database)
     * ===================================================================== */

    /** Greedy: score descending (tie: oldest report), least loaded technician (tie: first name). */
    @Transactional(readOnly = true)
    public Proposition proposerGlouton(LocalDate jour) {
        Contexte ctx = preparer(jour);
        Etat etat = ctx.initial().copie();
        completer(etat, parScore(ctx), ctx);
        return construire("Glouton", etat, ctx);
    }

    /** Greedy + local search (moves, removals/replacements) while the total value increases. */
    @Transactional(readOnly = true)
    public Proposition proposerRechercheLocale(LocalDate jour) {
        Contexte ctx = preparer(jour);
        Etat etat = ctx.initial().copie();
        completer(etat, parScore(ctx), ctx);
        return construire("Recherche locale", ameliorer(etat, ctx), ctx);
    }

    /** Baseline: requests treated by arrival order (oldest report date first), same assignment rule. */
    @Transactional(readOnly = true)
    public Proposition proposerOrdreArrivee(LocalDate jour) {
        Contexte ctx = preparer(jour);
        Etat etat = ctx.initial().copie();
        completer(etat, parOrdreArrivee(ctx), ctx);
        return construire("Ordre d'arrivée", etat, ctx);
    }

    @Transactional(readOnly = true)
    public Comparaison comparer(LocalDate jour) {
        return new Comparaison(proposerGlouton(jour), proposerRechercheLocale(jour),
                               proposerOrdreArrivee(jour));
    }

    /* =====================================================================
     *  2. ASSIGNMENT + CONFLICT DETECTION
     * ===================================================================== */

    /** Throws a BusinessRuleException naming the violated rule. */
    @Transactional(readOnly = true)
    public void verifierAffectation(Demande d, Utilisateur t, LocalDate jour) {
        if (d.getStatut() != Demande.StatutDemande.OUVERTE) {
            throw new BusinessRuleException(
                "Changement de statut non autorisé : la demande est " + d.getStatut()
                + ", seule une demande ouverte peut être affectée.");
        }
        if (!estCompetent(t, d)) {
            throw new BusinessRuleException(
                "Compétence manquante : " + t.getPrenom() + " n'a pas la compétence « "
                + d.getCategorie() + " » requise par la demande.");
        }
        if (estIndisponible(t, jour)) {
            throw new BusinessRuleException(
                "Technicien indisponible : " + t.getPrenom() + " est indisponible le " + jour + ".");
        }
        int charge = interventionDAO.chargeDuJour(t, jour);
        int total = charge + d.getDureeEstimee();
        if (total > CAPACITE_JOURNALIERE) {
            throw new BusinessRuleException(
                "Capacité journalière dépassée pour " + t.getPrenom() + " : " + charge + " h + "
                + d.getDureeEstimee() + " h = " + total + " h pour "
                + CAPACITE_JOURNALIERE + " h disponibles.");
        }
    }

    /** Manual assignment by the manager (scenario 3). */
    public Intervention affecter(Long idDemande, Long idTechnicien, LocalDate jour) {
        Demande d = demandeDAO.findById(idDemande)
            .orElseThrow(() -> new BusinessRuleException("Demande " + idDemande + " introuvable."));
        Utilisateur t = utilisateurDAO.findById(idTechnicien)
            .orElseThrow(() -> new BusinessRuleException("Technicien " + idTechnicien + " introuvable."));

        verifierAffectation(d, t, jour);
        Intervention intervention = interventionDAO.save(new Intervention(d, t, jour));
        // TODO (notifications): send "intervention affectée" mail to t
        return intervention;
    }

    /**
     * Validation of a plan (proposed, then possibly edited by the manager).
     * All-or-nothing: if one line breaks a rule, the exception rolls back the whole transaction.
     */
    public List<Intervention> valider(LocalDate jour, List<Choix> plan) {
        List<Intervention> crees = new ArrayList<>();
        for (Choix c : plan) {
            crees.add(affecter(c.idDemande(), c.idTechnicien(), jour));
        }
        return crees;
    }

    /* =====================================================================
     *  3. ALGORITHM INTERNALS
     * ===================================================================== */

    /** Mutable state of a plan: who gets what, and each technician's load for the day. */
    private static class Etat {
        final Map<Long, Integer> charges = new HashMap<>();
        final Map<Demande, Utilisateur> affectations = new LinkedHashMap<>();

        Etat copie() {
            Etat e = new Etat();
            e.charges.putAll(charges);
            e.affectations.putAll(affectations);
            return e;
        }

        int charge(Utilisateur t) {
            return charges.getOrDefault(t.getIdUtilisateur(), 0);
        }

        boolean peutRecevoir(Utilisateur t, Demande d) {
            return charge(t) + d.getDureeEstimee() <= CAPACITE_JOURNALIERE;
        }

        void affecter(Demande d, Utilisateur t) {
            affectations.put(d, t);
            charges.merge(t.getIdUtilisateur(), d.getDureeEstimee(), Integer::sum);
        }

        void retirer(Demande d) {
            Utilisateur t = affectations.remove(d);
            if (t != null) {
                charges.merge(t.getIdUtilisateur(), -d.getDureeEstimee(), Integer::sum);
            }
        }
    }

    /** Data loaded once per run: open requests, eligible technicians per request, starting loads. */
    private record Contexte(LocalDate jour, List<Demande> demandes,
                            Map<Demande, List<Utilisateur>> eligibles, Etat initial) { }

    private Contexte preparer(LocalDate jour) {
        List<Utilisateur> techniciens = utilisateurDAO.findByRole(Utilisateur.Role.TECHNICIEN)
            .stream().sorted(Comparator.comparing(Utilisateur::getPrenom)).toList();

        // only requests reported on or before the planned day
        List<Demande> demandes = demandeDAO.findByStatutAndDateSignalementLessThanEqual(
            Demande.StatutDemande.OUVERTE, jour);

        Map<Demande, List<Utilisateur>> eligibles = new HashMap<>();
        for (Demande d : demandes) {
            eligibles.put(d, techniciens.stream()
                .filter(t -> estCompetent(t, d) && !estIndisponible(t, jour))
                .toList());
        }

        Etat initial = new Etat();   // hours already assigned that day (e.g. earlier validated plans)
        for (Utilisateur t : techniciens) {
            initial.charges.put(t.getIdUtilisateur(), interventionDAO.chargeDuJour(t, jour));
        }
        return new Contexte(jour, demandes, eligibles, initial);
    }

    /** score = poids x (1 + calendar days between report date and planned day). */
    private static int score(Demande d, LocalDate jour) {
        long attente = ChronoUnit.DAYS.between(d.getDateSignalement(), jour);
        return (int) (d.getPriorite().getPoids() * (1 + attente));
    }

    private List<Demande> parScore(Contexte ctx) {
        return ctx.demandes().stream()
            .sorted(Comparator.comparingInt((Demande d) -> score(d, ctx.jour())).reversed()
                .thenComparing(Demande::getDateSignalement)
                .thenComparing(Demande::getIdDemande))
            .toList();
    }

    private List<Demande> parOrdreArrivee(Contexte ctx) {
        return ctx.demandes().stream()
            .sorted(Comparator.comparing(Demande::getDateSignalement)
                .thenComparing(Demande::getIdDemande))
            .toList();
    }

    /**
     * Assignment rule shared by all algorithms: for each not-yet-assigned request (in the given order),
     * pick the eligible technician with the lowest load whose remaining capacity is enough
     * (tie: alphabetical first name). If nobody fits, the request stays open.
     */
    private void completer(Etat etat, List<Demande> ordre, Contexte ctx) {
        for (Demande d : ordre) {
            if (etat.affectations.containsKey(d)) continue;
            ctx.eligibles().get(d).stream()
                .filter(t -> etat.peutRecevoir(t, d))
                .min(Comparator.<Utilisateur>comparingInt(etat::charge)
                        .thenComparing(Utilisateur::getPrenom))
                .ifPresent(t -> etat.affecter(d, t));
        }
    }

    private int valeur(Etat etat, LocalDate jour) {
        return etat.affectations.keySet().stream().mapToInt(d -> score(d, jour)).sum();
    }

    /** Hill climbing: take the best neighbour while it strictly increases the total value. */
    private Etat ameliorer(Etat depart, Contexte ctx) {
        List<Demande> ordre = parScore(ctx);
        Etat courant = depart;
        int valeurCourante = valeur(courant, ctx.jour());

        while (true) {
            Etat meilleur = null;
            int meilleureValeur = valeurCourante;
            for (Etat voisin : voisins(courant, ordre, ctx)) {
                int v = valeur(voisin, ctx.jour());
                if (v > meilleureValeur) {
                    meilleur = voisin;
                    meilleureValeur = v;
                }
            }
            if (meilleur == null) return courant;
            courant = meilleur;
            valeurCourante = meilleureValeur;
        }
    }

    /**
     * Neighbourhood: (a) try to insert unassigned requests, (b) remove one assignment then refill
     * (replacement), (c) move one assignment to another technician then refill.
     */
    private List<Etat> voisins(Etat courant, List<Demande> ordre, Contexte ctx) {
        List<Etat> resultat = new ArrayList<>();

        Etat insertion = courant.copie();
        completer(insertion, ordre, ctx);
        resultat.add(insertion);

        for (Demande d : courant.affectations.keySet()) {
            Etat remplacement = courant.copie();
            remplacement.retirer(d);
            completer(remplacement, ordre, ctx);
            resultat.add(remplacement);

            Long actuel = courant.affectations.get(d).getIdUtilisateur();
            for (Utilisateur t : ctx.eligibles().get(d)) {
                if (t.getIdUtilisateur().equals(actuel)) continue;
                Etat deplacement = courant.copie();
                deplacement.retirer(d);
                if (!deplacement.peutRecevoir(t, d)) continue;
                deplacement.affecter(d, t);
                completer(deplacement, ordre, ctx);
                resultat.add(deplacement);
            }
        }
        return resultat;
    }

    private Proposition construire(String algorithme, Etat etat, Contexte ctx) {
        List<Affectation> affectations = etat.affectations.entrySet().stream()
            .map(e -> new Affectation(e.getKey(), e.getValue(), score(e.getKey(), ctx.jour())))
            .sorted(Comparator.comparingInt(Affectation::score).reversed())
            .toList();
        List<Demande> nonAffectees = ctx.demandes().stream()
            .filter(d -> !etat.affectations.containsKey(d))
            .toList();
        long critiques = nonAffectees.stream()
            .filter(d -> d.getPriorite() == Demande.Priorite.CRITIQUE)
            .count();
        int total = affectations.stream().mapToInt(Affectation::score).sum();
        return new Proposition(algorithme, ctx.jour(), affectations, nonAffectees, total, critiques);
    }

    /* ---------- Skill / availability (adapt these two lines to your teammates' DAOs) ---------- */

    private boolean estCompetent(Utilisateur t, Demande d) {
        return technicienCompetenceDAO.existsByTechnicienAndCompetenceNom(t, d.getCategorie().name());
    }

    private boolean estIndisponible(Utilisateur t, LocalDate jour) {
        return indisponibiliteDAO.existsByTechnicienAndDate(t, jour);
    }
}

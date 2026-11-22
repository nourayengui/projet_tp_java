/* ============ Helpers shared by the three pages ============ */

const API = "/api";
const CAPACITE = 8;
const POIDS = { CRITIQUE: 8, HAUTE: 4, NORMALE: 2, BASSE: 1 };

async function api(url, options = {}) {
    const response = await fetch(API + url, {
        headers: { "Content-Type": "application/json" },
        ...options
    });
    let data = null;
    try { data = await response.json(); } catch (e) { /* empty body */ }
    if (!response.ok) {
        throw new Error((data && data.error) || "Erreur " + response.status);
    }
    return data;
}

function esc(value) {
    const div = document.createElement("div");
    div.textContent = value == null ? "" : String(value);
    return div.innerHTML;
}

function showMessage(text, type) {
    const box = document.getElementById("message");
    if (!box) return;
    box.textContent = text;
    box.className = type === "error" ? "message-error" : "message-success";
    window.scrollTo({ top: 0, behavior: "smooth" });
}

function today() {
    const d = new Date();
    const pad = n => String(n).padStart(2, "0");
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
}

function nomComplet(u) {
    return u ? (u.prenom + " " + (u.nom || "")).trim() : "";
}

function badge(value) {
    return '<span class="badge badge-' + esc(value) + '">' + esc(value) + "</span>";
}

function joursAttente(dateSignalement, jour) {
    return Math.round((new Date(jour) - new Date(dateSignalement)) / 86400000);
}

function score(demande, jour) {
    return POIDS[demande.priorite] * (1 + joursAttente(demande.dateSignalement, jour));
}

async function chargerTechniciens() {
    return api("/utilisateur?role=TECHNICIEN");           // P1 endpoint
}

/* ============ Page: planification.html ============ */

let techniciens = [];
let lignes = [];          // { demande, idTechnicien | null }
let jourPlanifie = null;

async function initPlanification() {
    document.getElementById("jour").value = today();
    try {
        techniciens = await chargerTechniciens();
    } catch (e) {
        showMessage(e.message, "error");
    }
    document.getElementById("btnProposer").onclick = proposer;
    document.getElementById("btnComparer").onclick = comparer;
    document.getElementById("btnValider").onclick = valider;
}

async function proposer() {
    const jour = document.getElementById("jour").value;
    const algo = document.getElementById("algo").value;
    if (!jour) return showMessage("Choisissez un jour.", "error");
    try {
        const p = await api("/planification/proposition?jour=" + jour + "&algo=" + algo);
        jourPlanifie = jour;
        lignes = [];
        p.affectations.forEach(a =>
            lignes.push({ demande: a.demande, idTechnicien: a.technicien.idUtilisateur }));
        p.nonAffectees.forEach(d => lignes.push({ demande: d, idTechnicien: null }));
        document.getElementById("zonePlan").classList.remove("hidden");
        afficherPlan();
        showMessage("Proposition « " + p.algorithme + " » générée. Vous pouvez la modifier avant de valider.", "success");
    } catch (e) {
        showMessage(e.message, "error");
    }
}

function afficherPlan() {
    const body = document.getElementById("planBody");
    body.innerHTML = "";
    lignes.forEach((l, index) => {
        const d = l.demande;
        const options = '<option value="">— non affectée —</option>' + techniciens.map(t =>
            '<option value="' + t.idUtilisateur + '"' +
            (t.idUtilisateur === l.idTechnicien ? " selected" : "") + ">" +
            esc(nomComplet(t)) + "</option>").join("");
        body.insertAdjacentHTML("beforeend",
            "<tr>" +
            "<td>D" + esc(d.idDemande) + "</td>" +
            "<td>" + esc(d.description) + "</td>" +
            "<td>" + esc(d.categorie) + "</td>" +
            "<td>" + badge(d.priorite) + "</td>" +
            "<td>" + esc(d.dureeEstimee) + " h</td>" +
            "<td>" + score(d, jourPlanifie) + "</td>" +
            '<td><select data-index="' + index + '">' + options + "</select></td>" +
            "</tr>");
    });
    body.querySelectorAll("select").forEach(s => s.onchange = e => {
        const v = e.target.value;
        lignes[e.target.dataset.index].idTechnicien = v ? Number(v) : null;
        afficherResume();
    });
    afficherResume();
}

function afficherResume() {
    const affectees = lignes.filter(l => l.idTechnicien !== null);
    const valeur = affectees.reduce((s, l) => s + score(l.demande, jourPlanifie), 0);
    const critiques = lignes.filter(l =>
        l.idTechnicien === null && l.demande.priorite === "CRITIQUE").length;

    let html = '<div class="card">Valeur totale<strong>' + valeur + "</strong></div>" +
               '<div class="card">Affectées<strong>' + affectees.length + " / " + lignes.length + "</strong></div>" +
               '<div class="card">Critiques non traitées<strong>' + critiques + "</strong></div>";

    techniciens.forEach(t => {
        const charge = affectees
            .filter(l => l.idTechnicien === t.idUtilisateur)
            .reduce((s, l) => s + l.demande.dureeEstimee, 0);
        const classe = charge > CAPACITE ? "charge-over" : "charge-ok";
        html += '<div class="card">' + esc(t.prenom) +
                '<strong class="' + classe + '">' + charge + " h / " + CAPACITE + " h</strong></div>";
    });
    document.getElementById("resume").innerHTML = html;
}

async function comparer() {
    const jour = document.getElementById("jour").value;
    if (!jour) return showMessage("Choisissez un jour.", "error");
    try {
        const c = await api("/planification/comparaison?jour=" + jour);
        const body = document.getElementById("compBody");
        body.innerHTML = "";
        [c.glouton, c.rechercheLocale, c.ordreArrivee].forEach(p => {
            body.insertAdjacentHTML("beforeend",
                "<tr><td>" + esc(p.algorithme) + "</td>" +
                "<td><strong>" + p.valeurTotale + "</strong></td>" +
                "<td>" + p.affectations.length + "</td>" +
                "<td>" + p.critiquesNonTraitees + "</td></tr>");
        });
        document.getElementById("zoneComp").classList.remove("hidden");
    } catch (e) {
        showMessage(e.message, "error");
    }
}

async function valider() {
    const plan = lignes
        .filter(l => l.idTechnicien !== null)
        .map(l => ({ idDemande: l.demande.idDemande, idTechnicien: l.idTechnicien }));
    if (plan.length === 0) return showMessage("Aucune affectation à valider.", "error");
    if (!confirm("Valider " + plan.length + " affectation(s) pour le " + jourPlanifie + " ?")) return;
    try {
        await api("/planification/valider", {
            method: "POST",
            body: JSON.stringify({ jour: jourPlanifie, plan })
        });
        showMessage("Planification validée : " + plan.length + " intervention(s) créée(s).", "success");
        document.getElementById("zonePlan").classList.add("hidden");
    } catch (e) {
        // conflict: the rule violated is named by the server, nothing was saved
        showMessage("Conflit : " + e.message, "error");
    }
}

/* ============ Page: liste.html ============ */

async function initListe() {
    document.getElementById("filtreStatut").onchange = chargerListe;
    document.getElementById("filtreJour").onchange = chargerListe;
    document.getElementById("btnReset").onclick = () => {
        document.getElementById("filtreStatut").value = "";
        document.getElementById("filtreJour").value = "";
        chargerListe();
    };
    chargerListe();
}

async function chargerListe() {
    const statut = document.getElementById("filtreStatut").value;
    const jour = document.getElementById("filtreJour").value;
    try {
        let interventions = await api("/intervention");
        if (statut) interventions = interventions.filter(i => i.statut === statut);
        if (jour) interventions = interventions.filter(i => i.datePlanifiee === jour);

        const body = document.getElementById("listeBody");
        body.innerHTML = "";
        if (interventions.length === 0) {
            body.innerHTML = '<tr><td colspan="7">Aucune intervention.</td></tr>';
            return;
        }
        interventions.forEach(i => {
            let actions = "";
            if (i.statut === "ASSIGNEE") {
                actions += '<button class="btn-primary" onclick="demarrer(' + i.idIntervention + ')">Démarrer</button>';
            }
            if (i.statut === "EN_COURS") {
                actions += '<a href="../compterendu/formulaire.html?idIntervention=' + i.idIntervention +
                           '"><button class="btn-success">Terminer</button></a>';
            }
            if (i.statut === "ASSIGNEE" || i.statut === "EN_COURS") {
                actions += '<button class="btn-danger" onclick="annuler(' + i.idIntervention + ')">Annuler</button>';
            }
            body.insertAdjacentHTML("beforeend",
                "<tr><td>" + esc(i.idIntervention) + "</td>" +
                "<td>D" + esc(i.demande.idDemande) + " — " + esc(i.demande.description) + "</td>" +
                "<td>" + esc(nomComplet(i.technicien)) + "</td>" +
                "<td>" + esc(i.datePlanifiee) + "</td>" +
                "<td>" + esc(i.demande.dureeEstimee) + " h</td>" +
                "<td>" + badge(i.statut) + "</td>" +
                "<td>" + actions + "</td></tr>");
        });
    } catch (e) {
        showMessage(e.message, "error");
    }
}

async function demarrer(id) {
    try {
        await api("/intervention/" + id + "/demarrer", { method: "POST" });
        showMessage("Intervention démarrée.", "success");
        chargerListe();
    } catch (e) { showMessage(e.message, "error"); }
}

async function annuler(id) {
    if (!confirm("Annuler cette intervention ?")) return;
    try {
        await api("/intervention/" + id + "/annuler", { method: "POST" });
        showMessage("Intervention annulée.", "success");
        chargerListe();
    } catch (e) { showMessage(e.message, "error"); }
}

/* ============ Page: affectation.html ============ */

async function initAffectation() {
    document.getElementById("jour").value = today();
    try {
        const [demandes, techs] = await Promise.all([
            api("/demande?statut=OUVERTE"),                // P2 endpoint
            chargerTechniciens()
        ]);
        document.getElementById("demande").innerHTML = demandes.map(d =>
            '<option value="' + d.idDemande + '">D' + esc(d.idDemande) + " — " +
            esc(d.description) + " (" + esc(d.categorie) + ", " + esc(d.priorite) + ", " +
            esc(d.dureeEstimee) + " h)</option>").join("");
        document.getElementById("technicien").innerHTML = techs.map(t =>
            '<option value="' + t.idUtilisateur + '">' + esc(nomComplet(t)) + "</option>").join("");
    } catch (e) {
        showMessage(e.message, "error");
    }
    document.getElementById("btnAffecter").onclick = affecter;
}

async function affecter() {
    const idDemande = Number(document.getElementById("demande").value);
    const idTechnicien = Number(document.getElementById("technicien").value);
    const jour = document.getElementById("jour").value;
    if (!idDemande || !idTechnicien || !jour) {
        return showMessage("Remplissez tous les champs.", "error");
    }
    try {
        await api("/planification/affecter", {
            method: "POST",
            body: JSON.stringify({ idDemande, idTechnicien, jour })
        });
        showMessage("Intervention affectée avec succès.", "success");
        initAffectation();                                  // refresh open requests
    } catch (e) {
        showMessage("Conflit : " + e.message, "error");     // rule violated, named by the server
    }
}

/* ===== Configuration =====
 * USE_MOCK = true  : fonctionne sans back-end (données dans le navigateur) pour tester.
 * USE_MOCK = false : appelle l'API REST Java :
 *   GET    /api/me                       -> {id, nom, role}  (role: DEMANDEUR | RESPONSABLE)
 *   GET    /api/equipements              -> [{id, designation, lieu}]
 *   GET    /api/demandes                 -> [Demande]  (filtré par rôle côté serveur)
 *   POST   /api/demandes                 -> crée
 *   PUT    /api/demandes/{id}            -> modifie (si OUVERTE)
 *   DELETE /api/demandes/{id}            -> supprime (responsable)
 *   POST   /api/demandes/{id}/annuler    -> annule
 *   PUT    /api/demandes/{id}/priorite   -> {priorite} (responsable)
 * Demande JSON : {id, description, equipementId, demandeurId, categorie, priorite,
 *                 dureeEstimee, statut, dateSignalement:"YYYY-MM-DD"}
 * En cas d'erreur, le serveur renvoie {message:"..."} avec un code 4xx. */
const CONFIG = { USE_MOCK: true, API: "/api" };

const PRIORITES = { CRITIQUE: { label: "Critique", poids: 8 }, HAUTE: { label: "Haute", poids: 4 },
                    NORMALE: { label: "Normale", poids: 2 }, BASSE: { label: "Basse", poids: 1 } };
const CATEGORIES = { RESEAU: "Réseau", MATERIEL: "Matériel", LOGICIEL: "Logiciel" };
const STATUTS = { OUVERTE: "Ouverte", ASSIGNEE: "Assignée", EN_COURS: "En cours", TERMINEE: "Terminée", ANNULEE: "Annulée" };

const $ = id => document.getElementById(id);
const today = () => new Date().toISOString().slice(0, 10);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const daysBetween = (a, b) => Math.round((new Date(b) - new Date(a)) / 86400000);

/* ===== API réelle ===== */
async function http(method, url, body) {
  const res = await fetch(CONFIG.API + url, {
    method, credentials: "same-origin",
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined
  });
  if (!res.ok) {
    let msg = res.statusText;
    try { msg = (await res.json()).message || msg; } catch (e) { /* ignore */ }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}
const realApi = {
  me: () => http("GET", "/me"),
  equipements: () => http("GET", "/equipements"),
  list: () => http("GET", "/demandes"),
  create: d => http("POST", "/demandes", d),
  update: (id, d) => http("PUT", "/demandes/" + id, d),
  remove: id => http("DELETE", "/demandes/" + id),
  annuler: id => http("POST", `/demandes/${id}/annuler`),
  setPriorite: (id, priorite) => http("PUT", `/demandes/${id}/priorite`, { priorite })
};

/* ===== API simulée (mêmes règles que la classe Java Demande) ===== */
const MOCK_USERS = [
  { id: 3, nom: "Sarra", role: "DEMANDEUR" }, { id: 1, nom: "Rania", role: "DEMANDEUR" },
  { id: 2, nom: "Anis", role: "DEMANDEUR" }, { id: 9, nom: "Mehdi", role: "RESPONSABLE" }
];
const MOCK_EQUIP = [
  { id: 1, designation: "Serveur de fichiers", lieu: "Salle des serveurs" }, { id: 2, designation: "Imprimante", lieu: "Salle B" },
  { id: 3, designation: "Projecteur", lieu: "Salle A" }, { id: 4, designation: "Point d'accès wifi", lieu: "Bloc C" },
  { id: 5, designation: "PC de la scolarité", lieu: "Administration" }, { id: 6, designation: "Poste de travail", lieu: "Salle C" }
];
let mockUser = MOCK_USERS[0];
const store = {
  load() { try { return JSON.parse(localStorage.getItem("mock_demandes")) || []; } catch (e) { return []; } },
  save(a) { try { localStorage.setItem("mock_demandes", JSON.stringify(a)); } catch (e) { /* ignore */ } }
};
function checkDuree(d) {
  if (!Number.isInteger(d) || d < 1 || d > 8)
    throw new Error(`Durée estimée invalide (${d} h) : elle doit être comprise entre 1 et 8 heures.`);
}
const mockApi = {
  me: async () => mockUser,
  equipements: async () => MOCK_EQUIP,
  list: async () => {
    const all = store.load();
    return mockUser.role === "RESPONSABLE" ? all : all.filter(d => d.demandeurId === mockUser.id);
  },
  create: async d => {
    checkDuree(d.dureeEstimee);
    const all = store.load();
    const nd = { ...d, id: all.reduce((m, x) => Math.max(m, x.id), 0) + 1, demandeurId: mockUser.id, statut: "OUVERTE" };
    all.push(nd); store.save(all); return nd;
  },
  update: async (id, d) => {
    checkDuree(d.dureeEstimee);
    const all = store.load(); const x = all.find(o => o.id === id);
    if (!x || x.statut !== "OUVERTE") throw new Error("Modification refusée : seule une demande ouverte peut être modifiée.");
    Object.assign(x, d, { id, demandeurId: x.demandeurId, statut: x.statut });
    store.save(all); return x;
  },
  remove: async id => {
    if (mockUser.role !== "RESPONSABLE") throw new Error("Suppression réservée au responsable.");
    store.save(store.load().filter(d => d.id !== id));
  },
  annuler: async id => {
    const all = store.load(); const x = all.find(o => o.id === id);
    const resp = mockUser.role === "RESPONSABLE";
    if (x.statut === "TERMINEE" || x.statut === "ANNULEE") throw new Error(`Annulation impossible : la demande est déjà ${STATUTS[x.statut].toLowerCase()}.`);
    if (!resp && x.statut !== "OUVERTE" && x.statut !== "ASSIGNEE")
      throw new Error("Annulation refusée : une demande en cours ne peut être annulée que par le responsable.");
    x.statut = "ANNULEE"; store.save(all);
  },
  setPriorite: async (id, p) => {
    if (mockUser.role !== "RESPONSABLE") throw new Error("Changement de priorité réservé au responsable.");
    const all = store.load(); all.find(o => o.id === id).priorite = p; store.save(all);
  }
};
const api = CONFIG.USE_MOCK ? mockApi : realApi;

/* ===== État et affichage ===== */
let me = null, equipements = [], demandes = [];

function toast(msg, isErr) {
  const t = $("toast"); t.textContent = msg; t.className = isErr ? "err" : ""; t.hidden = false;
  clearTimeout(toast.h); toast.h = setTimeout(() => (t.hidden = true), 3500);
}
function fillSelect(sel, map, allLabel) {
  sel.innerHTML = (allLabel ? `<option value="">${allLabel}</option>` : "") +
    Object.entries(map).map(([k, v]) => `<option value="${k}">${esc(v.label || v)}</option>`).join("");
}
const equipName = id => { const e = equipements.find(x => x.id === id); return e ? `${e.designation} (${e.lieu})` : "?"; };
const score = d => PRIORITES[d.priorite].poids * (1 + Math.max(0, daysBetween(d.dateSignalement, $("jourPlan").value)));

function renderStats() {
  const c = s => demandes.filter(d => d.statut === s).length;
  $("stats").innerHTML = `<div class="stat"><b>${demandes.length}</b><span>Total</span></div>` +
    Object.entries(STATUTS).map(([k, v]) => `<div class="stat"><b>${c(k)}</b><span>${v}</span></div>`).join("");
}

function renderRows() {
  const q = $("search").value.toLowerCase();
  const list = demandes.filter(d =>
    (!$("fStatut").value || d.statut === $("fStatut").value) &&
    (!$("fCategorie").value || d.categorie === $("fCategorie").value) &&
    (!$("fPriorite").value || d.priorite === $("fPriorite").value) &&
    (!q || (d.description + " " + equipName(d.equipementId)).toLowerCase().includes(q)));
  const resp = me.role === "RESPONSABLE";
  $("rows").innerHTML = list.map(d => {
    const peutAnnuler = resp ? !["TERMINEE", "ANNULEE"].includes(d.statut) : ["OUVERTE", "ASSIGNEE"].includes(d.statut);
    return `<tr>
      <td>D${d.id}</td><td>${esc(d.description)}</td><td>${esc(equipName(d.equipementId))}</td>
      <td>${CATEGORIES[d.categorie]}</td>
      <td>${resp && d.statut !== "TERMINEE" && d.statut !== "ANNULEE"
        ? `<select class="sm" data-prio="${d.id}">${Object.entries(PRIORITES).map(([k, v]) =>
            `<option value="${k}" ${k === d.priorite ? "selected" : ""}>${v.label}</option>`).join("")}</select>`
        : `<span class="badge p-${d.priorite}">${PRIORITES[d.priorite].label}</span>`}</td>
      <td>${d.dureeEstimee} h</td><td>${esc(d.dateSignalement)}</td><td>${score(d)}</td>
      <td><span class="badge s-${d.statut}">${STATUTS[d.statut]}</span></td>
      <td><div class="row-actions">
        ${d.statut === "OUVERTE" ? `<button class="sm" data-edit="${d.id}">Modifier</button>` : ""}
        ${peutAnnuler ? `<button class="sm" data-cancel="${d.id}">Annuler</button>` : ""}
        ${resp ? `<button class="sm danger" data-del="${d.id}">Supprimer</button>` : ""}
      </div></td></tr>`;
  }).join("");
  $("empty").hidden = list.length > 0;
}

async function refresh() {
  try { demandes = await api.list(); } catch (e) { toast(e.message, true); demandes = []; }
  renderStats(); renderRows();
}

/* ===== Formulaire ===== */
function resetForm() {
  $("demandeForm").reset(); $("demandeId").value = ""; $("dateSignalement").value = today();
  $("formTitle").textContent = "Nouvelle demande"; $("btnSave").textContent = "Envoyer la demande";
  $("btnCancelEdit").hidden = true; $("formError").hidden = true;
}
function showFormError(msg) { $("formError").textContent = msg; $("formError").hidden = !msg; }

$("demandeForm").addEventListener("submit", async ev => {
  ev.preventDefault(); showFormError("");
  const duree = Number($("duree").value);
  const data = {
    description: $("description").value.trim(), equipementId: Number($("equipement").value),
    categorie: $("categorie").value, priorite: $("priorite").value,
    dureeEstimee: duree, dateSignalement: $("dateSignalement").value || today()
  };
  if (!data.description) return showFormError("La description est obligatoire.");
  if (!Number.isInteger(duree) || duree < 1 || duree > 8)
    return showFormError(`Durée estimée invalide (${$("duree").value} h) : elle doit être comprise entre 1 et 8 heures.`);
  try {
    const id = $("demandeId").value;
    if (id) { await api.update(Number(id), data); toast("Demande modifiée."); }
    else { await api.create(data); toast("Demande enregistrée."); }
    resetForm(); await refresh();
  } catch (e) { showFormError(e.message); toast(e.message, true); }
});
$("btnCancelEdit").addEventListener("click", resetForm);

/* ===== Actions du tableau ===== */
$("rows").addEventListener("click", async ev => {
  const b = ev.target.closest("button"); if (!b) return;
  try {
    if (b.dataset.edit) {
      const d = demandes.find(x => x.id === Number(b.dataset.edit));
      $("demandeId").value = d.id; $("description").value = d.description; $("equipement").value = d.equipementId;
      $("categorie").value = d.categorie; $("priorite").value = d.priorite; $("duree").value = d.dureeEstimee;
      $("dateSignalement").value = d.dateSignalement;
      $("formTitle").textContent = "Modifier la demande D" + d.id; $("btnSave").textContent = "Enregistrer";
      $("btnCancelEdit").hidden = false; window.scrollTo({ top: 0, behavior: "smooth" });
    } else if (b.dataset.cancel) {
      if (!confirm("Annuler cette demande ?")) return;
      await api.annuler(Number(b.dataset.cancel)); toast("Demande annulée."); await refresh();
    } else if (b.dataset.del) {
      if (!confirm("Supprimer définitivement cette demande ?")) return;
      await api.remove(Number(b.dataset.del)); toast("Demande supprimée."); await refresh();
    }
  } catch (e) { toast(e.message, true); await refresh(); }
});
$("rows").addEventListener("change", async ev => {
  const s = ev.target.closest("select[data-prio]"); if (!s) return;
  try { await api.setPriorite(Number(s.dataset.prio), s.value); toast("Priorité mise à jour (score recalculé)."); }
  catch (e) { toast(e.message, true); }
  await refresh();
});
["search", "fStatut", "fCategorie", "fPriorite", "jourPlan"].forEach(id => $(id).addEventListener("input", renderRows));

/* ===== Démarrage ===== */
async function init() {
  fillSelect($("categorie"), CATEGORIES); fillSelect($("priorite"), PRIORITES); $("priorite").value = "NORMALE";
  fillSelect($("fStatut"), STATUTS, "Tous les statuts"); fillSelect($("fCategorie"), CATEGORIES, "Toutes catégories");
  fillSelect($("fPriorite"), PRIORITES, "Toutes priorités");
  $("jourPlan").value = today(); $("dateSignalement").value = today();

  if (CONFIG.USE_MOCK) {
    $("mockUser").innerHTML = MOCK_USERS.map(u => `<option value="${u.id}">${u.nom} (${u.role.toLowerCase()})</option>`).join("");
    $("mockUser").addEventListener("change", async () => {
      mockUser = MOCK_USERS.find(u => u.id === Number($("mockUser").value)); await start();
    });
  } else { $("mockUser").closest("label").hidden = true; }
  await start();
}
async function start() {
  try {
    me = await api.me(); equipements = await api.equipements();
    $("equipement").innerHTML = equipements.map(e => `<option value="${e.id}">${esc(e.designation)} (${esc(e.lieu)})</option>`).join("");
    $("roleBadge").textContent = `${me.nom} · ${me.role.toLowerCase()}`;
    resetForm(); await refresh();
  } catch (e) { toast("Erreur de chargement : " + e.message, true); }
}
init();

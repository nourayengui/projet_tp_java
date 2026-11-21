const API_URL = "../api/equipements";

const form = document.getElementById("equipement-form");
const idInput = document.getElementById("idEquipement");
const designationInput = document.getElementById("designation");
const lieuInput = document.getElementById("lieu");
const body = document.getElementById("equipements-body");
const message = document.getElementById("message");
const formTitle = document.getElementById("form-title");
const cancelButton = document.getElementById("cancel-button");


// ==============================
// Charger les équipements
// ==============================

async function chargerEquipements() {

    try {

        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error("Erreur lors du chargement.");
        }

        const equipements = await response.json();

        afficherEquipements(equipements);

    }
    catch (error) {

        afficherMessage(
            error.message,
            false
        );
    }
}


// ==============================
// Afficher les équipements
// ==============================

function afficherEquipements(equipements) {

    body.innerHTML = "";

    equipements.forEach(equipement => {

        const row = document.createElement("tr");

        row.innerHTML = `
            <td>${equipement.idEquipement}</td>

            <td>
                ${escapeHtml(equipement.designation)}
            </td>

            <td>
                ${escapeHtml(equipement.lieu)}
            </td>

            <td>

                <button
                    onclick="modifierEquipement(
                        ${equipement.idEquipement},
                        '${escapeJs(equipement.designation)}',
                        '${escapeJs(equipement.lieu)}'
                    )"
                >
                    Modifier
                </button>

                <button
                    onclick="supprimerEquipement(
                        ${equipement.idEquipement}
                    )"
                >
                    Supprimer
                </button>

            </td>
        `;

        body.appendChild(row);
    });
}


// ==============================
// Ajouter / modifier
// ==============================

form.addEventListener("submit", async function (event) {

    event.preventDefault();

    const id = idInput.value;

    const data = new URLSearchParams();

    data.append(
        "designation",
        designationInput.value
    );

    data.append(
        "lieu",
        lieuInput.value
    );


    try {

        let response;

        if (id === "") {

            // Ajouter
            response = await fetch(API_URL, {
                method: "POST",
                body: data
            });

        }
        else {

            // Modifier
            response = await fetch(
                `${API_URL}/${id}`,
                {
                    method: "PUT",
                    body: data
                }
            );
        }


        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.message);
        }


        afficherMessage(
            result.message,
            true
        );

        resetForm();

        await chargerEquipements();

    }
    catch (error) {

        afficherMessage(
            error.message,
            false
        );
    }
});


// ==============================
// Préparer modification
// ==============================

function modifierEquipement(
    id,
    designation,
    lieu
) {

    idInput.value = id;

    designationInput.value = designation;

    lieuInput.value = lieu;

    formTitle.textContent =
        "Modifier un équipement";

    cancelButton.hidden = false;
}


// ==============================
// Annuler modification
// ==============================

cancelButton.addEventListener(
    "click",
    resetForm
);


function resetForm() {

    form.reset();

    idInput.value = "";

    formTitle.textContent =
        "Ajouter un équipement";

    cancelButton.hidden = true;
}


// ==============================
// Supprimer
// ==============================

async function supprimerEquipement(id) {

    const confirmation =
        confirm(
            "Voulez-vous vraiment supprimer cet équipement ?"
        );

    if (!confirmation) {
        return;
    }


    try {

        const response = await fetch(
            `${API_URL}/${id}`,
            {
                method: "DELETE"
            }
        );

        const result =
            await response.json();


        if (!response.ok) {
            throw new Error(result.message);
        }


        afficherMessage(
            result.message,
            true
        );

        await chargerEquipements();

    }
    catch (error) {

        afficherMessage(
            error.message,
            false
        );
    }
}


// ==============================
// Messages
// ==============================

function afficherMessage(
    texte,
    success
) {

    message.textContent = texte;

    message.className =
        success
            ? "message-success"
            : "message-error";
}


// ==============================
// Sécurité affichage HTML
// ==============================

function escapeHtml(text) {

    return text
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}


function escapeJs(text) {

    return text
        .replaceAll("\\", "\\\\")
        .replaceAll("'", "\\'")
        .replaceAll("\n", "\\n")
        .replaceAll("\r", "\\r");
}


// ==============================
// Démarrage
// ==============================

chargerEquipements();

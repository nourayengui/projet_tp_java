-- Schéma de la base MySQL (propriétaire : Noura, seule personne autorisée à modifier ce fichier)
CREATE DATABASE IF NOT EXISTS maintenance CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE maintenance;

CREATE TABLE IF NOT EXISTS utilisateur (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    prenom        VARCHAR(100) NOT NULL,
    nom           VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    mot_de_passe  VARCHAR(255) NOT NULL,          -- haché (BCrypt), jamais en clair
    role          ENUM('DEMANDEUR','TECHNICIEN','RESPONSABLE') NOT NULL
);

CREATE TABLE IF NOT EXISTS equipement (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    designation  VARCHAR(150) NOT NULL,
    lieu         VARCHAR(150) NOT NULL
);

-- compétences d'un technicien (un technicien peut en avoir plusieurs)
CREATE TABLE IF NOT EXISTS competence (
    technicien_id BIGINT NOT NULL,
    categorie     ENUM('RESEAU','MATERIEL','LOGICIEL') NOT NULL,
    PRIMARY KEY (technicien_id, categorie),
    FOREIGN KEY (technicien_id) REFERENCES utilisateur(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS indisponibilite (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    technicien_id BIGINT NOT NULL,
    jour          DATE NOT NULL,
    motif         VARCHAR(150),
    UNIQUE (technicien_id, jour),
    FOREIGN KEY (technicien_id) REFERENCES utilisateur(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS demande (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    description      VARCHAR(255) NOT NULL,
    equipement_id    BIGINT NOT NULL,
    demandeur_id     BIGINT NOT NULL,
    categorie        ENUM('RESEAU','MATERIEL','LOGICIEL') NOT NULL,
    priorite         ENUM('CRITIQUE','HAUTE','NORMALE','BASSE') NOT NULL,
    duree_estimee    TINYINT NOT NULL CHECK (duree_estimee BETWEEN 1 AND 8),
    statut           ENUM('OUVERTE','ASSIGNEE','EN_COURS','TERMINEE','ANNULEE') NOT NULL DEFAULT 'OUVERTE',
    date_signalement DATE NOT NULL,
    FOREIGN KEY (equipement_id) REFERENCES equipement(id),
    FOREIGN KEY (demandeur_id)  REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS intervention (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    demande_id        BIGINT NOT NULL UNIQUE,
    technicien_id     BIGINT NOT NULL,
    date_intervention DATE NOT NULL,
    date_fin          DATE NULL,
    FOREIGN KEY (demande_id)    REFERENCES demande(id) ON DELETE CASCADE,
    FOREIGN KEY (technicien_id) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS compte_rendu (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    intervention_id  BIGINT NOT NULL UNIQUE,
    cause            VARCHAR(255) NOT NULL,
    action_realisee  VARCHAR(255) NOT NULL,
    duree_reelle     TINYINT NOT NULL CHECK (duree_reelle > 0),
    FOREIGN KEY (intervention_id) REFERENCES intervention(id) ON DELETE CASCADE
);

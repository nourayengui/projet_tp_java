package com.maintenance.dao;

import com.maintenance.model.Equipement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EquipementDAO {

    // Récupérer tous les équipements
    public List<Equipement> findAll() throws SQLException {

        List<Equipement> equipements = new ArrayList<>();

        String sql = """
                SELECT idEquipement, designation, lieu
                FROM Equipement
                ORDER BY idEquipement
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()
        ) {

            while (rs.next()) {

                Equipement equipement = new Equipement();

                equipement.setIdEquipement(
                        rs.getInt("idEquipement")
                );

                equipement.setDesignation(
                        rs.getString("designation")
                );

                equipement.setLieu(
                        rs.getString("lieu")
                );

                equipements.add(equipement);
            }
        }

        return equipements;
    }


    // Récupérer un équipement par son ID
    public Equipement findById(int id) throws SQLException {

        String sql = """
                SELECT idEquipement, designation, lieu
                FROM Equipement
                WHERE idEquipement = ?
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setInt(1, id);

            try (ResultSet rs = statement.executeQuery()) {

                if (rs.next()) {

                    Equipement equipement = new Equipement();

                    equipement.setIdEquipement(
                            rs.getInt("idEquipement")
                    );

                    equipement.setDesignation(
                            rs.getString("designation")
                    );

                    equipement.setLieu(
                            rs.getString("lieu")
                    );

                    return equipement;
                }
            }
        }

        return null;
    }


    // Ajouter un équipement
    public void insert(Equipement equipement) throws SQLException {

        String sql = """
                INSERT INTO Equipement(designation, lieu)
                VALUES (?, ?)
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, equipement.getDesignation());
            statement.setString(2, equipement.getLieu());

            statement.executeUpdate();
        }
    }


    // Modifier un équipement
    public boolean update(Equipement equipement) throws SQLException {

        String sql = """
                UPDATE Equipement
                SET designation = ?, lieu = ?
                WHERE idEquipement = ?
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, equipement.getDesignation());
            statement.setString(2, equipement.getLieu());
            statement.setInt(3, equipement.getIdEquipement());

            return statement.executeUpdate() > 0;
        }
    }


    // Vérifier si l'équipement existe
    public boolean existsById(int id) throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM Equipement
                WHERE idEquipement = ?
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setInt(1, id);

            try (ResultSet rs = statement.executeQuery()) {

                rs.next();

                return rs.getInt(1) > 0;
            }
        }
    }


    // Vérifier si un équipement possède des demandes
    public boolean hasDemandes(int idEquipement) throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM Demande
                WHERE idEquipement = ?
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setInt(1, idEquipement);

            try (ResultSet rs = statement.executeQuery()) {

                rs.next();

                return rs.getInt(1) > 0;
            }
        }
    }


    // Supprimer un équipement
    public boolean delete(int id) throws SQLException {

        String sql = """
                DELETE FROM Equipement
                WHERE idEquipement = ?
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setInt(1, id);

            return statement.executeUpdate() > 0;
        }
    }
}

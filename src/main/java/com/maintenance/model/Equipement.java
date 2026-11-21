package com.maintenance.model;

public class Equipement {

    private int idEquipement;
    private String designation;
    private String lieu;

    public Equipement() {
    }

    public Equipement(int idEquipement, String designation, String lieu) {
        this.idEquipement = idEquipement;
        this.designation = designation;
        this.lieu = lieu;
    }

    public int getIdEquipement() {
        return idEquipement;
    }

    public String getDesignation() {
        return designation;
    }

    public String getLieu() {
        return lieu;
    }

    public void setIdEquipement(int idEquipement) {
        this.idEquipement = idEquipement;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public void setLieu(String lieu) {
        this.lieu = lieu;
    }
}

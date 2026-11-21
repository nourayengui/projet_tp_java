package com.maintenance.controller;

import com.maintenance.model.Equipement;
import com.maintenance.service.EquipementService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet("/api/equipements/*")
public class EquipementServlet extends HttpServlet {

    private EquipementService equipementService;

    @Override
    public void init() {
        equipementService = new EquipementService();
    }


    // GET
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getPathInfo();

        try (PrintWriter out = response.getWriter()) {

            // GET /api/equipements
            if (path == null || path.equals("/")) {

                List<Equipement> equipements =
                        equipementService.getAllEquipements();

                out.print(toJson(equipements));
                return;
            }


            // GET /api/equipements/5
            int id = parseId(path);

            Equipement equipement =
                    equipementService.getEquipementById(id);

            if (equipement == null) {

                response.setStatus(
                        HttpServletResponse.SC_NOT_FOUND
                );

                out.print("""
                        {"success":false,"message":"Équipement introuvable."}
                        """);

                return;
            }

            out.print(toJson(equipement));
        }
        catch (IllegalArgumentException e) {

            response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
            );

            response.getWriter().print(
                    jsonError(e.getMessage())
            );

        }
        catch (Exception e) {

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().print(
                    jsonError("Erreur serveur.")
            );
        }
    }


    // POST = ajouter
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String designation =
                request.getParameter("designation");

        String lieu =
                request.getParameter("lieu");

        try {

            equipementService.ajouterEquipement(
                    designation,
                    lieu
            );

            response.setStatus(
                    HttpServletResponse.SC_CREATED
            );

            response.getWriter().print("""
                    {"success":true,"message":"Équipement ajouté avec succès."}
                    """);

        }
        catch (IllegalArgumentException e) {

            response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
            );

            response.getWriter().print(
                    jsonError(e.getMessage())
            );

        }
        catch (Exception e) {

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().print(
                    jsonError("Erreur serveur.")
            );
        }
    }


    // PUT = modifier
    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getPathInfo();

        try {

            int id = parseId(path);

            String designation =
                    request.getParameter("designation");

            String lieu =
                    request.getParameter("lieu");

            equipementService.modifierEquipement(
                    id,
                    designation,
                    lieu
            );

            response.getWriter().print("""
                    {"success":true,"message":"Équipement modifié avec succès."}
                    """);

        }
        catch (IllegalArgumentException e) {

            response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
            );

            response.getWriter().print(
                    jsonError(e.getMessage())
            );

        }
        catch (Exception e) {

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().print(
                    jsonError("Erreur serveur.")
            );
        }
    }


    // DELETE = supprimer
    @Override
    protected void doDelete(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getPathInfo();

        try {

            int id = parseId(path);

            equipementService.supprimerEquipement(id);

            response.getWriter().print("""
                    {"success":true,"message":"Équipement supprimé avec succès."}
                    """);

        }
        catch (IllegalArgumentException e) {

            response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
            );

            response.getWriter().print(
                    jsonError(e.getMessage())
            );

        }
        catch (IllegalStateException e) {

            response.setStatus(
                    HttpServletResponse.SC_CONFLICT
            );

            response.getWriter().print(
                    jsonError(e.getMessage())
            );

        }
        catch (Exception e) {

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().print(
                    jsonError("Erreur serveur.")
            );
        }
    }


    private int parseId(String path) {

        if (path == null || path.equals("/")) {
            throw new IllegalArgumentException(
                    "ID équipement manquant."
            );
        }

        String idString =
                path.substring(1);

        try {
            return Integer.parseInt(idString);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ID équipement invalide."
            );
        }
    }


    private String jsonError(String message) {

        return "{\"success\":false,\"message\":\""
                + escapeJson(message)
                + "\"}";
    }


    private String toJson(Equipement e) {

        return """
                {
                    "idEquipement": %d,
                    "designation": "%s",
                    "lieu": "%s"
                }
                """.formatted(
                e.getIdEquipement(),
                escapeJson(e.getDesignation()),
                escapeJson(e.getLieu())
        );
    }


    private String toJson(List<Equipement> equipements) {

        StringBuilder json =
                new StringBuilder("[");

        for (int i = 0; i < equipements.size(); i++) {

            json.append(toJson(equipements.get(i)));

            if (i < equipements.size() - 1) {
                json.append(",");
            }
        }

        json.append("]");

        return json.toString();
    }


    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

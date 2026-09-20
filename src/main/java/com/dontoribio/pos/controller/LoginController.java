package com.dontoribio.pos.controller;

import com.dontoribio.pos.MainApp;
import com.dontoribio.pos.dao.UsuarioDAO;
import com.dontoribio.pos.dao.impl.UsuarioDAOImpl;
import com.dontoribio.pos.model.Usuario;
import com.dontoribio.pos.util.PasswordUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Optional;

/** Controlador de la pantalla de autenticacion. Valida credenciales contra BCrypt. */
public class LoginController {

    @FXML
    private TextField campoUsername;

    @FXML
    private PasswordField campoPassword;

    @FXML
    private Label labelError;

    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();

    @FXML
    private void initialize() {
        labelError.setText("");
    }

    @FXML
    private void onIniciarSesion(ActionEvent event) {
        String username = campoUsername.getText() == null ? "" : campoUsername.getText().trim();
        String password = campoPassword.getText();

        if (username.isEmpty() || password == null || password.isEmpty()) {
            labelError.setText("Ingrese usuario y contrasena");
            return;
        }

        Optional<Usuario> usuarioOpt = usuarioDAO.buscarPorUsername(username);
        if (usuarioOpt.isEmpty() || !usuarioOpt.get().isActivo()
                || !PasswordUtil.verificarPassword(password, usuarioOpt.get().getPasswordHash())) {
            labelError.setText("Usuario o contrasena incorrectos");
            return;
        }

        Usuario usuario = usuarioOpt.get();
        MainApp.getInstance().mostrarPantallaPrincipal(usuario);
    }
}

package com.dontoribio.pos.controller;

import com.dontoribio.pos.dao.VentaDAO;
import com.dontoribio.pos.dao.impl.VentaDAOImpl;
import com.dontoribio.pos.model.Pago;
import com.dontoribio.pos.model.Venta;
import com.dontoribio.pos.service.TicketGeneratorService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/** Controlador del historial de ventas del dia, con opcion para reimprimir el comprobante de cada venta. */
public class HistorialVentasController {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    private TableView<Venta> tablaVentas;
    @FXML
    private TableColumn<Venta, String> colHora;
    @FXML
    private TableColumn<Venta, String> colCliente;
    @FXML
    private TableColumn<Venta, String> colTotal;
    @FXML
    private TableColumn<Venta, String> colMetodoPago;
    @FXML
    private TableColumn<Venta, String> colComprobante;
    @FXML
    private TableColumn<Venta, Void> colVer;
    @FXML
    private Label labelResumen;

    private final VentaDAO ventaDAO = new VentaDAOImpl();

    @FXML
    private void initialize() {
        colHora.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFecha().format(FORMATO_HORA)));
        colCliente.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getCliente().getNombreRazonSocial()));
        colTotal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                "S/ " + data.getValue().getTotal().setScale(2, RoundingMode.HALF_UP)));
        colMetodoPago.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getPagos().stream()
                        .map(Pago::getMetodoPago)
                        .map(Enum::name)
                        .collect(Collectors.joining(" + "))));
        colComprobante.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getTipoComprobante().name()));

        colVer.setCellFactory(columna -> new TableCell<>() {
            private final Button boton = new Button("Ver");

            {
                boton.setOnAction(e -> verComprobante(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setGraphic(vacio ? null : boton);
            }
        });

        cargarVentasDelDia();
    }

    private void cargarVentasDelDia() {
        var ventas = ventaDAO.listarPorFecha(LocalDate.now());
        tablaVentas.setItems(FXCollections.observableArrayList(ventas));
        var total = ventas.stream().map(Venta::getTotal).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        labelResumen.setText(ventas.size() + " venta(s) hoy - Total: S/ " + total.setScale(2, RoundingMode.HALF_UP));
    }

    private void verComprobante(Venta ventaResumen) {
        ventaDAO.buscarVentaCompleta(ventaResumen.getId()).ifPresentOrElse(ventaCompleta -> {
            String ticket = TicketGeneratorService.generar(ventaCompleta, ventaCompleta.getUsuario(), null, null);
            TicketGeneratorService.mostrarVistaPrevia(ticket);
        }, () -> new Alert(Alert.AlertType.ERROR, "No se pudo cargar el comprobante de la venta").showAndWait());
    }
}

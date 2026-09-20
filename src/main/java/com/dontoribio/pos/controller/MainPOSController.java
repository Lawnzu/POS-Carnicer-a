package com.dontoribio.pos.controller;

import com.dontoribio.pos.dao.ProductoDAO;
import com.dontoribio.pos.dao.VentaDAO;
import com.dontoribio.pos.dao.impl.ProductoDAOImpl;
import com.dontoribio.pos.dao.impl.VentaDAOImpl;
import com.dontoribio.pos.model.*;
import com.dontoribio.pos.service.BalanzaSimuladaService;
import com.dontoribio.pos.service.IBalanzaService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador de la pantalla principal del POS: visor de pesaje en vivo,
 * gestion de hasta 7 listas de atencion en paralelo y registro de venta.
 */
public class MainPOSController {

    @FXML
    private Label labelUsuarioActivo;
    @FXML
    private Label labelPesoActual;
    @FXML
    private Label labelImporteCalculado;
    @FXML
    private ComboBox<Producto> comboProducto;
    @FXML
    private ComboBox<TipoCliente> comboTipoCliente;
    @FXML
    private ComboBox<MetodoPago> comboMetodoPago;
    @FXML
    private TabPane tabPaneListas;
    @FXML
    private Label labelTotalLista;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final VentaDAO ventaDAO = new VentaDAOImpl();
    private final IBalanzaService balanzaService = new BalanzaSimuladaService();

    private final Map<Integer, ListaAtencion> listasAtencion = new HashMap<>();
    private final Map<Integer, TableView<DetalleVenta>> tablasPorLista = new HashMap<>();

    private Usuario usuarioSesion;
    private double pesoActualKg = 0.0;

    @FXML
    private void initialize() {
        comboTipoCliente.setItems(FXCollections.observableArrayList(TipoCliente.values()));
        comboMetodoPago.setItems(FXCollections.observableArrayList(MetodoPago.values()));
        comboProducto.setItems(FXCollections.observableArrayList(productoDAO.listarActivos()));

        construirTabsMultilista();
        iniciarBalanza();
    }

    /** Recibe el usuario autenticado desde LoginController al cambiar de pantalla. */
    public void setUsuarioSesion(Usuario usuario) {
        this.usuarioSesion = usuario;
        labelUsuarioActivo.setText(usuario.getNombreCompleto() + " (" + usuario.getRol() + ")");
    }

    private void construirTabsMultilista() {
        for (int numero = 1; numero <= ListaAtencion.MAX_LISTAS; numero++) {
            ListaAtencion lista = new ListaAtencion(numero);
            listasAtencion.put(numero, lista);

            TableView<DetalleVenta> tabla = crearTablaDetalle();
            tablasPorLista.put(numero, tabla);

            Tab tab = new Tab("Lista " + numero, tabla);
            tab.setClosable(false);
            tabPaneListas.getTabs().add(tab);
        }

        tabPaneListas.getSelectionModel().selectedIndexProperty()
                .addListener((obs, anterior, nuevo) -> actualizarTotalListaActiva());
    }

    private TableView<DetalleVenta> crearTablaDetalle() {
        TableView<DetalleVenta> tabla = new TableView<>();

        TableColumn<DetalleVenta, String> colProducto = new TableColumn<>("Producto");
        colProducto.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getProducto().getNombre()));

        TableColumn<DetalleVenta, BigDecimal> colPeso = new TableColumn<>("Peso (kg)");
        colPeso.setCellValueFactory(new PropertyValueFactory<>("pesoKg"));

        TableColumn<DetalleVenta, BigDecimal> colPrecio = new TableColumn<>("Precio/kg");
        colPrecio.setCellValueFactory(new PropertyValueFactory<>("precioUnitario"));

        TableColumn<DetalleVenta, BigDecimal> colImporte = new TableColumn<>("Importe");
        colImporte.setCellValueFactory(new PropertyValueFactory<>("importe"));

        tabla.getColumns().addAll(colProducto, colPeso, colPrecio, colImporte);
        return tabla;
    }

    private void iniciarBalanza() {
        try {
            balanzaService.conectar();
            balanzaService.iniciarLecturaContinua(new com.dontoribio.pos.service.PesoListener() {
                @Override
                public void onPesoRecibido(double pesoKg) {
                    pesoActualKg = pesoKg;
                    labelPesoActual.setText(String.format("%.3f kg", pesoKg));
                    actualizarImporteCalculado();
                }

                @Override
                public void onError(String mensaje) {
                    labelPesoActual.setText("-- kg");
                    labelImporteCalculado.setText("S/ --");
                }
            });
        } catch (Exception e) {
            labelPesoActual.setText("Balanza no disponible");
        }
    }

    private void actualizarImporteCalculado() {
        Producto producto = comboProducto.getValue();
        TipoCliente tipoCliente = comboTipoCliente.getValue();
        if (producto == null || tipoCliente == null) {
            labelImporteCalculado.setText("S/ --");
            return;
        }
        BigDecimal precioKg = producto.getPrecioPara(tipoCliente);
        BigDecimal importe = precioKg.multiply(BigDecimal.valueOf(pesoActualKg))
                .setScale(2, RoundingMode.HALF_UP);
        labelImporteCalculado.setText("S/ " + importe);
    }

    @FXML
    private void onAgregarPesajeALista() {
        Producto producto = comboProducto.getValue();
        TipoCliente tipoCliente = comboTipoCliente.getValue();
        if (producto == null || tipoCliente == null) {
            new Alert(Alert.AlertType.WARNING, "Seleccione producto y tipo de cliente").showAndWait();
            return;
        }

        BigDecimal peso = BigDecimal.valueOf(pesoActualKg).setScale(3, RoundingMode.HALF_UP);
        BigDecimal precioKg = producto.getPrecioPara(tipoCliente);
        DetalleVenta detalle = new DetalleVenta(producto, peso, precioKg);

        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        ListaAtencion lista = listasAtencion.get(numeroListaActiva);
        lista.agregarItem(detalle);

        ObservableList<DetalleVenta> items = FXCollections.observableArrayList(lista.getItems());
        tablasPorLista.get(numeroListaActiva).setItems(items);

        actualizarTotalListaActiva();
    }

    private void actualizarTotalListaActiva() {
        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        ListaAtencion lista = listasAtencion.get(numeroListaActiva);
        if (lista != null) {
            labelTotalLista.setText("Total: S/ " + lista.getTotal().setScale(2, RoundingMode.HALF_UP));
        }
    }

    @FXML
    private void onCobrarListaActiva() {
        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        ListaAtencion lista = listasAtencion.get(numeroListaActiva);
        MetodoPago metodoPago = comboMetodoPago.getValue();

        if (lista.estaVacia()) {
            new Alert(Alert.AlertType.WARNING, "La lista esta vacia").showAndWait();
            return;
        }
        if (metodoPago == null) {
            new Alert(Alert.AlertType.WARNING, "Seleccione un metodo de pago").showAndWait();
            return;
        }

        Venta venta = new Venta();
        venta.setUsuario(usuarioSesion);
        venta.setCliente(lista.getCliente());
        lista.getItems().forEach(venta::agregarDetalle);
        venta.agregarPago(new Pago(metodoPago, venta.getTotal(), null));

        ventaDAO.registrarVenta(venta);

        lista.limpiar();
        tablasPorLista.get(numeroListaActiva).setItems(FXCollections.observableArrayList());
        actualizarTotalListaActiva();

        new Alert(Alert.AlertType.INFORMATION, "Venta registrada correctamente").showAndWait();
    }
}

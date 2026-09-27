package com.dontoribio.pos.controller;

import com.dontoribio.pos.dao.CajaDAO;
import com.dontoribio.pos.dao.ClienteDAO;
import com.dontoribio.pos.dao.ProductoDAO;
import com.dontoribio.pos.dao.VentaDAO;
import com.dontoribio.pos.dao.impl.CajaDAOImpl;
import com.dontoribio.pos.dao.impl.ClienteDAOImpl;
import com.dontoribio.pos.dao.impl.ProductoDAOImpl;
import com.dontoribio.pos.dao.impl.VentaDAOImpl;
import com.dontoribio.pos.model.*;
import com.dontoribio.pos.service.BalanzaSimuladaService;
import com.dontoribio.pos.service.IBalanzaService;
import com.dontoribio.pos.service.TicketGeneratorService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Controlador de la pantalla principal del POS: visor de pesaje en vivo,
 * gestion de hasta 7 listas de atencion en paralelo y registro de venta.
 */
public class MainPOSController {

    /** Cortes de mayor rotacion mostrados como atajos de un clic sobre el visor de pesaje. */
    private static final List<String> CORTES_RAPIDOS = List.of(
            "Panceta de Cerdo", "Chuleta de Cerdo", "Lomo Fino de Res",
            "Brazo de Cerdo (Paleta)", "Pierna de Cerdo", "Cerdo Entero"
    );

    private static final String DOCUMENTO_CLIENTE_GENERICO = "00000000";
    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d*");

    @FXML
    private Label labelUsuarioActivo;
    @FXML
    private Label labelPesoActual;
    @FXML
    private Label labelPrecioKg;
    @FXML
    private Label labelImporteCalculado;
    @FXML
    private ComboBox<Producto> comboProducto;
    @FXML
    private ComboBox<TipoCliente> comboTipoCliente;
    @FXML
    private ComboBox<MetodoPago> comboMetodoPago;
    @FXML
    private ComboBox<TipoComprobante> comboTipoComprobante;
    @FXML
    private TextField campoDocumentoCliente;
    @FXML
    private TabPane tabPaneListas;
    @FXML
    private Label labelTotalLista;
    @FXML
    private TilePane tilePaneAtajos;
    @FXML
    private TextField campoEfectivoEntregado;
    @FXML
    private Label labelVuelto;
    @FXML
    private TextField campoBuscarProducto;
    @FXML
    private TextField campoNombreCliente;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final VentaDAO ventaDAO = new VentaDAOImpl();
    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final CajaDAO cajaDAO = new CajaDAOImpl();
    private final IBalanzaService balanzaService = new BalanzaSimuladaService();

    private final Map<Integer, ListaAtencion> listasAtencion = new HashMap<>();
    private final Map<Integer, TableView<DetalleVenta>> tablasPorLista = new HashMap<>();

    private Usuario usuarioSesion;
    private Caja cajaActual;
    private double pesoActualKg = 0.0;
    private Cliente clienteGenerico;
    private ObservableList<Producto> productosBase;
    private FilteredList<Producto> productosFiltrados;

    @FXML
    private void initialize() {
        comboTipoCliente.setItems(FXCollections.observableArrayList(TipoCliente.values()));
        comboTipoCliente.setValue(TipoCliente.MINORISTA);
        comboMetodoPago.setItems(FXCollections.observableArrayList(MetodoPago.values()));

        comboTipoComprobante.setItems(FXCollections.observableArrayList(TipoComprobante.values()));
        comboTipoComprobante.setValue(TipoComprobante.TICKET_SIMPLE);
        aplicarRestriccionDocumento(TipoComprobante.TICKET_SIMPLE);

        productosBase = FXCollections.observableArrayList(productoDAO.listarActivos());
        productosFiltrados = new FilteredList<>(productosBase, p -> true);
        comboProducto.setItems(productosFiltrados);

        construirTabsMultilista();
        construirAtajosRapidos(productosBase);
        iniciarBalanza();

        campoEfectivoEntregado.setDisable(true);
        campoEfectivoEntregado.textProperty().addListener((obs, anterior, nuevo) -> actualizarVuelto());
        comboTipoCliente.setOnAction(e -> actualizarImporteCalculado());

        campoBuscarProducto.textProperty().addListener((obs, anterior, nuevo) -> {
            String filtro = nuevo == null ? "" : nuevo.trim().toLowerCase();
            productosFiltrados.setPredicate(producto -> filtro.isEmpty()
                    || producto.getNombre().toLowerCase().contains(filtro)
                    || producto.getCodigo().toLowerCase().contains(filtro));
            if (filtro.isEmpty()) {
                comboProducto.hide();
            } else if (!productosFiltrados.isEmpty()) {
                comboProducto.show();
            } else {
                comboProducto.hide();
            }
        });
    }

    /** Recibe el usuario autenticado desde LoginController al cambiar de pantalla. */
    public void setUsuarioSesion(Usuario usuario) {
        this.usuarioSesion = usuario;
        labelUsuarioActivo.setText("Cajero: " + usuario.getNombreCompleto() + " (" + usuario.getRol() + ")");
        try {
            verificarAperturaCaja();
        } catch (RuntimeException e) {
            cajaActual = null;
            new Alert(Alert.AlertType.WARNING,
                    "No se pudo verificar/abrir la caja (puede que la base de datos no tenga la tabla 'caja' "
                            + "todavia; ejecute schema.sql). El POS continuara sin control de caja.\n\n" + e.getMessage())
                    .showAndWait();
        }
    }

    /** Recupera la caja abierta del turno o solicita el monto inicial de efectivo para abrir una nueva. */
    private void verificarAperturaCaja() {
        Optional<Caja> cajaAbierta = cajaDAO.buscarCajaAbierta();
        if (cajaAbierta.isPresent()) {
            cajaActual = cajaAbierta.get();
            return;
        }

        BigDecimal montoInicial = solicitarMontoInicialCaja();

        Caja caja = new Caja();
        caja.setUsuario(usuarioSesion);
        caja.setMontoInicial(montoInicial.setScale(2, RoundingMode.HALF_UP));
        cajaActual = cajaDAO.abrirCaja(caja);
    }

    /** Pide el monto inicial de caja hasta recibir un numero valido y no negativo, o 0 si el usuario cancela. */
    private BigDecimal solicitarMontoInicialCaja() {
        while (true) {
            TextInputDialog dialogo = new TextInputDialog("0.00");
            dialogo.setTitle("Apertura de caja");
            dialogo.setHeaderText("Inicio de turno - " + usuarioSesion.getNombreCompleto());
            dialogo.setContentText("Monto inicial de efectivo en caja (S/):");

            Optional<String> respuesta = dialogo.showAndWait();
            if (respuesta.isEmpty()) {
                return BigDecimal.ZERO;
            }

            BigDecimal parseado = parsearMonto(respuesta.get());
            if (parseado != null && parseado.signum() >= 0) {
                return parseado;
            }
            new Alert(Alert.AlertType.WARNING, "El monto inicial debe ser un numero valido y no negativo").showAndWait();
        }
    }

    @FXML
    private void onCerrarCaja() {
        if (cajaActual == null) {
            new Alert(Alert.AlertType.WARNING, "No hay una caja abierta").showAndWait();
            return;
        }

        Map<MetodoPago, BigDecimal> resumen = cajaDAO.resumenVentasDesde(cajaActual.getFechaApertura());
        BigDecimal totalVentas = BigDecimal.ZERO;
        StringBuilder detalle = new StringBuilder();
        detalle.append("Monto inicial: S/ ").append(cajaActual.getMontoInicial().setScale(2, RoundingMode.HALF_UP)).append('\n');
        for (MetodoPago metodo : MetodoPago.values()) {
            BigDecimal monto = resumen.getOrDefault(metodo, BigDecimal.ZERO);
            totalVentas = totalVentas.add(monto);
            detalle.append(metodo.name()).append(": S/ ").append(monto.setScale(2, RoundingMode.HALF_UP)).append('\n');
        }
        detalle.append("Total vendido: S/ ").append(totalVentas.setScale(2, RoundingMode.HALF_UP));

        BigDecimal efectivoVendido = resumen.getOrDefault(MetodoPago.EFECTIVO, BigDecimal.ZERO);
        BigDecimal montoFinal = cajaActual.getMontoInicial().add(efectivoVendido).setScale(2, RoundingMode.HALF_UP);
        detalle.append("\nEfectivo esperado en caja: S/ ").append(montoFinal);

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION, detalle.toString(), ButtonType.OK, ButtonType.CANCEL);
        confirmacion.setTitle("Cierre de caja");
        confirmacion.setHeaderText("Resumen del turno");
        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }

        cajaDAO.cerrarCaja(cajaActual.getId(), LocalDateTime.now(), montoFinal);
        cajaActual = null;
        new Alert(Alert.AlertType.INFORMATION, "Caja cerrada correctamente").showAndWait();
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

    /** Crea un boton (con imagen, nombre y codigo) por cada corte de {@link #CORTES_RAPIDOS} que exista en el catalogo cargado. */
    private void construirAtajosRapidos(List<Producto> productos) {
        for (String nombreCorte : CORTES_RAPIDOS) {
            productos.stream()
                    .filter(p -> p.getNombre().equalsIgnoreCase(nombreCorte))
                    .findFirst()
                    .ifPresent(producto -> {
                        Button boton = new Button(producto.getNombre() + "\n" + producto.getCodigo());
                        boton.setPrefWidth(130.0);
                        boton.setPrefHeight(90.0);
                        boton.setWrapText(true);
                        boton.setContentDisplay(ContentDisplay.TOP);

                        Image imagen = cargarImagen(producto.getImagenPath());
                        if (imagen != null) {
                            ImageView vista = new ImageView(imagen);
                            vista.setFitWidth(48.0);
                            vista.setFitHeight(48.0);
                            vista.setPreserveRatio(true);
                            boton.setGraphic(vista);
                        }

                        boton.setOnAction(e -> {
                            comboProducto.setValue(producto);
                            actualizarImporteCalculado();
                        });
                        tilePaneAtajos.getChildren().add(boton);
                    });
        }
    }

    /** Carga una imagen desde una ruta local; retorna null si la ruta esta vacia, el archivo no existe o no es valido. */
    private Image cargarImagen(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        File archivo = new File(ruta.trim());
        if (!archivo.isFile()) {
            return null;
        }
        try {
            Image imagen = new Image(archivo.toURI().toString());
            return imagen.isError() ? null : imagen;
        } catch (RuntimeException e) {
            return null;
        }
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

        TableColumn<DetalleVenta, BigDecimal> colImporte = new TableColumn<>("Importe (S/)");
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
            labelPrecioKg.setText("Precio/kg: S/ --");
            return;
        }
        BigDecimal precioKg = producto.getPrecioPara(tipoCliente);
        labelPrecioKg.setText("Precio/kg: S/ " + precioKg.setScale(2, RoundingMode.HALF_UP));
        BigDecimal importe = precioKg.multiply(BigDecimal.valueOf(pesoActualKg))
                .setScale(2, RoundingMode.HALF_UP);
        labelImporteCalculado.setText("S/ " + importe);
    }

    @FXML
    private void onProductoSeleccionado() {
        actualizarImporteCalculado();
    }

    @FXML
    private void onMetodoPagoSeleccionado() {
        MetodoPago metodoPago = comboMetodoPago.getValue();
        boolean esEfectivo = metodoPago == MetodoPago.EFECTIVO;
        campoEfectivoEntregado.setDisable(!esEfectivo);
        if (!esEfectivo) {
            campoEfectivoEntregado.clear();
            labelVuelto.setText("Vuelto: S/ 0.00");
        }
    }

    @FXML
    private void onTipoComprobanteSeleccionado() {
        TipoComprobante tipo = comboTipoComprobante.getValue();
        aplicarRestriccionDocumento(tipo == null ? TipoComprobante.TICKET_SIMPLE : tipo);
    }

    /** Habilita el campo de documento y limita su longitud/formato segun el tipo de comprobante elegido. */
    private void aplicarRestriccionDocumento(TipoComprobante tipo) {
        campoDocumentoCliente.clear();
        if (tipo == TipoComprobante.TICKET_SIMPLE) {
            campoDocumentoCliente.setDisable(true);
            campoDocumentoCliente.setTextFormatter(null);
            return;
        }

        campoDocumentoCliente.setDisable(false);
        int maxDigitos = tipo == TipoComprobante.BOLETA ? 8 : 11;
        campoDocumentoCliente.setPromptText(tipo == TipoComprobante.BOLETA ? "DNI (8 digitos)" : "RUC (11 digitos)");

        UnaryOperator<TextFormatter.Change> filtro = cambio -> {
            String textoNuevo = cambio.getControlNewText();
            if (textoNuevo.length() <= maxDigitos && SOLO_DIGITOS.matcher(textoNuevo).matches()) {
                return cambio;
            }
            return null;
        };
        campoDocumentoCliente.setTextFormatter(new TextFormatter<>(filtro));
    }

    private void actualizarVuelto() {
        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        ListaAtencion lista = listasAtencion.get(numeroListaActiva);
        BigDecimal total = lista != null ? lista.getTotal() : BigDecimal.ZERO;

        BigDecimal entregado = parsearMonto(campoEfectivoEntregado.getText());
        if (entregado == null) {
            labelVuelto.setText("Vuelto: S/ 0.00");
            return;
        }
        BigDecimal vuelto = entregado.subtract(total).setScale(2, RoundingMode.HALF_UP);
        labelVuelto.setText("Vuelto: S/ " + vuelto);
    }

    private BigDecimal parsearMonto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
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

        refrescarTablaLista(numeroListaActiva);
        actualizarTotalListaActiva();
    }

    @FXML
    private void onEliminarItemSeleccionado() {
        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        TableView<DetalleVenta> tabla = tablasPorLista.get(numeroListaActiva);
        DetalleVenta seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            new Alert(Alert.AlertType.WARNING, "Seleccione un item de la lista para eliminar").showAndWait();
            return;
        }

        listasAtencion.get(numeroListaActiva).quitarItem(seleccionado);
        refrescarTablaLista(numeroListaActiva);
        actualizarTotalListaActiva();
    }

    @FXML
    private void onVaciarListaActiva() {
        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        ListaAtencion lista = listasAtencion.get(numeroListaActiva);
        if (lista.estaVacia()) {
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "Se eliminaran todos los items de la Lista " + numeroListaActiva + ". Continuar?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmacion.setTitle("Vaciar lista");
        confirmacion.setHeaderText(null);
        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == ButtonType.OK) {
            lista.limpiar();
            refrescarTablaLista(numeroListaActiva);
            actualizarTotalListaActiva();
        }
    }

    private void refrescarTablaLista(int numeroLista) {
        ObservableList<DetalleVenta> items = FXCollections.observableArrayList(listasAtencion.get(numeroLista).getItems());
        tablasPorLista.get(numeroLista).setItems(items);
    }

    private void actualizarTotalListaActiva() {
        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        ListaAtencion lista = listasAtencion.get(numeroListaActiva);
        if (lista != null) {
            labelTotalLista.setText("Total: S/ " + lista.getTotal().setScale(2, RoundingMode.HALF_UP));
        }
        actualizarVuelto();
    }

    /** Cliente por defecto ("Venta a publico") usado cuando la lista no tiene un cliente identificado. */
    private Cliente obtenerClienteGenerico() {
        if (clienteGenerico != null) {
            return clienteGenerico;
        }
        Optional<Cliente> existente = clienteDAO.buscarPorDocumento(DOCUMENTO_CLIENTE_GENERICO);
        if (existente.isPresent()) {
            clienteGenerico = existente.get();
            return clienteGenerico;
        }
        Cliente nuevo = new Cliente();
        nuevo.setTipoCliente(TipoCliente.MINORISTA);
        nuevo.setNombreRazonSocial("VTA PUBLICO");
        nuevo.setDocumento(DOCUMENTO_CLIENTE_GENERICO);
        clienteGenerico = clienteDAO.guardar(nuevo);
        return clienteGenerico;
    }

    @FXML
    private void onCobrarListaActiva() {
        int numeroListaActiva = tabPaneListas.getSelectionModel().getSelectedIndex() + 1;
        ListaAtencion lista = listasAtencion.get(numeroListaActiva);
        MetodoPago metodoPago = comboMetodoPago.getValue();
        TipoComprobante tipoComprobante = comboTipoComprobante.getValue() == null
                ? TipoComprobante.TICKET_SIMPLE : comboTipoComprobante.getValue();

        if (lista.estaVacia()) {
            new Alert(Alert.AlertType.WARNING, "La lista esta vacia").showAndWait();
            return;
        }
        if (metodoPago == null) {
            new Alert(Alert.AlertType.WARNING, "Seleccione un metodo de pago").showAndWait();
            return;
        }

        String documento = campoDocumentoCliente.getText() == null ? "" : campoDocumentoCliente.getText().trim();
        if (tipoComprobante == TipoComprobante.BOLETA && documento.length() != 8) {
            new Alert(Alert.AlertType.WARNING, "El DNI para boleta debe tener exactamente 8 digitos").showAndWait();
            return;
        }
        if (tipoComprobante == TipoComprobante.FACTURA && documento.length() != 11) {
            new Alert(Alert.AlertType.WARNING, "El RUC para factura debe tener exactamente 11 digitos").showAndWait();
            return;
        }

        BigDecimal total = lista.getTotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoRecibido;
        BigDecimal vuelto;

        if (metodoPago == MetodoPago.EFECTIVO) {
            montoRecibido = parsearMonto(campoEfectivoEntregado.getText());
            if (montoRecibido == null || montoRecibido.compareTo(total) < 0) {
                new Alert(Alert.AlertType.WARNING, "El efectivo entregado debe ser un monto valido y no menor al total").showAndWait();
                return;
            }
            vuelto = montoRecibido.subtract(total).setScale(2, RoundingMode.HALF_UP);
        } else {
            montoRecibido = total;
            vuelto = BigDecimal.ZERO;
        }

        lista.setCliente(resolverClienteDeLista());

        Venta venta = new Venta();
        venta.setUsuario(usuarioSesion);
        venta.setCliente(lista.getCliente());
        venta.setTipoComprobante(tipoComprobante);
        venta.setDocumentoCliente(documento.isEmpty() ? null : documento);
        lista.getItems().forEach(venta::agregarDetalle);
        venta.agregarPago(new Pago(metodoPago, venta.getTotal(), null));

        try {
            ventaDAO.registrarVenta(venta);
        } catch (IllegalStateException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
            return;
        } catch (RuntimeException e) {
            new Alert(Alert.AlertType.ERROR, "No se pudo registrar la venta: " + e.getMessage()).showAndWait();
            return;
        }

        lista.limpiar();
        refrescarTablaLista(numeroListaActiva);
        campoEfectivoEntregado.clear();
        campoNombreCliente.clear();
        comboTipoComprobante.setValue(TipoComprobante.TICKET_SIMPLE);
        aplicarRestriccionDocumento(TipoComprobante.TICKET_SIMPLE);
        actualizarTotalListaActiva();

        String ticket = TicketGeneratorService.generar(venta, usuarioSesion, montoRecibido, vuelto);
        TicketGeneratorService.mostrarVistaPrevia(ticket);
    }

    /** Resuelve el cliente de la venta a partir del nombre ingresado, o el cliente generico "VTA PUBLICO". */
    private Cliente resolverClienteDeLista() {
        String nombreCliente = campoNombreCliente.getText() == null ? "" : campoNombreCliente.getText().trim();
        if (nombreCliente.isEmpty()) {
            return obtenerClienteGenerico();
        }
        return clienteDAO.buscarPorNombre(nombreCliente).orElseGet(() -> {
            Cliente nuevo = new Cliente();
            nuevo.setNombreRazonSocial(nombreCliente);
            TipoCliente tipo = comboTipoCliente.getValue();
            nuevo.setTipoCliente(tipo != null ? tipo : TipoCliente.MINORISTA);
            return clienteDAO.guardar(nuevo);
        });
    }

    /** Recarga el catalogo desde la BD tras cerrar la ventana de Catalogo (precios/stock pudieron cambiar). */
    private void recargarCatalogoProductos() {
        productosBase.setAll(productoDAO.listarActivos());
        tilePaneAtajos.getChildren().clear();
        construirAtajosRapidos(productosBase);
    }

    @FXML
    private void onAbrirCatalogo() {
        abrirVentana("catalogo.fxml", "Catalogo de productos");
    }

    @FXML
    private void onAbrirCompras() {
        abrirVentana("compras.fxml", "Compras / Ingreso de mercaderia");
    }

    @FXML
    private void onAbrirHistorialVentas() {
        abrirVentana("historial_ventas.fxml", "Historial de ventas del dia");
    }

    private void abrirVentana(String fxml, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/dontoribio/pos/view/" + fxml));
            Parent root = loader.load();

            if (fxml.equals("compras.fxml")) {
                ComprasController controller = loader.getController();
                controller.setUsuarioSesion(usuarioSesion);
            }

            Stage ventana = new Stage();
            ventana.setTitle(titulo);
            Scene scene = new Scene(root, 900, 600);
            scene.getStylesheets().add(getClass().getResource("/com/dontoribio/pos/view/styles.css").toExternalForm());
            ventana.setScene(scene);
            if (fxml.equals("catalogo.fxml")) {
                ventana.setOnHidden(e -> recargarCatalogoProductos());
            }
            ventana.show();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "No se pudo abrir " + titulo + ": " + e.getMessage()).showAndWait();
        }
    }
}

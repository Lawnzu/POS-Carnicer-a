package com.dontoribio.pos.controller;

import com.dontoribio.pos.dao.ProductoDAO;
import com.dontoribio.pos.dao.impl.ProductoDAOImpl;
import com.dontoribio.pos.model.Producto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controlador de la ventana de Catalogo/Inventario: alta, edicion y desactivacion
 * de productos. Los precios por kg son fijos y solo cambian aqui (nunca en el POS).
 */
public class CatalogoController {

    private static final String FILTRO_TODOS = "Todos";
    private static final String FILTRO_ACTIVOS = "Activos";
    private static final String FILTRO_INACTIVOS = "Inactivos";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Path CARPETA_IMAGENES_PRODUCTOS = Paths.get("img", "productos");

    @FXML
    private TableView<Producto> tablaProductos;
    @FXML
    private TableColumn<Producto, String> colCodigo;
    @FXML
    private TableColumn<Producto, String> colNombre;
    @FXML
    private TableColumn<Producto, BigDecimal> colPrecioMinorista;
    @FXML
    private TableColumn<Producto, BigDecimal> colPrecioMayorista;
    @FXML
    private TableColumn<Producto, BigDecimal> colStock;
    @FXML
    private TableColumn<Producto, String> colLote;
    @FXML
    private TableColumn<Producto, LocalDate> colFechaProduccion;
    @FXML
    private TableColumn<Producto, LocalDate> colFechaVencimiento;
    @FXML
    private TableColumn<Producto, Boolean> colActivo;

    @FXML
    private TextField campoBuscarProducto;
    @FXML
    private ComboBox<String> comboFiltroEstado;

    @FXML
    private TextField campoCodigo;
    @FXML
    private TextField campoNombre;
    @FXML
    private TextField campoPrecioMinorista;
    @FXML
    private TextField campoPrecioMayorista;
    @FXML
    private TextField campoStock;
    @FXML
    private TextField campoImagenPath;
    @FXML
    private CheckBox checkActivo;
    @FXML
    private Label labelLoteActual;
    @FXML
    private Label labelFechaProduccionActual;
    @FXML
    private Label labelFechaVencimientoActual;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private Producto productoSeleccionado;
    private ObservableList<Producto> productosBase;
    private FilteredList<Producto> productosFiltrados;

    @FXML
    private void initialize() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colPrecioMinorista.setCellValueFactory(new PropertyValueFactory<>("precioKgMinorista"));
        colPrecioMayorista.setCellValueFactory(new PropertyValueFactory<>("precioKgMayorista"));

        colStock.setCellValueFactory(new PropertyValueFactory<>("stockDisponible"));
        colStock.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP).toPlainString());
            }
        });

        colLote.setCellValueFactory(new PropertyValueFactory<>("loteActual"));
        colFechaProduccion.setCellValueFactory(new PropertyValueFactory<>("fechaProduccionActual"));
        colFechaProduccion.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : valor.format(FORMATO_FECHA));
            }
        });
        colFechaVencimiento.setCellValueFactory(new PropertyValueFactory<>("fechaVencimientoActual"));
        colFechaVencimiento.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : valor.format(FORMATO_FECHA));
            }
        });

        colActivo.setCellValueFactory(new PropertyValueFactory<>("activo"));

        comboFiltroEstado.setItems(FXCollections.observableArrayList(FILTRO_TODOS, FILTRO_ACTIVOS, FILTRO_INACTIVOS));
        comboFiltroEstado.setValue(FILTRO_TODOS);
        comboFiltroEstado.setOnAction(e -> aplicarFiltros());
        campoBuscarProducto.textProperty().addListener((obs, anterior, nuevo) -> aplicarFiltros());

        cargarTabla();
        onNuevoProducto();
    }

    private void aplicarFiltros() {
        if (productosFiltrados == null) {
            return;
        }
        String filtroTexto = campoBuscarProducto.getText() == null
                ? "" : campoBuscarProducto.getText().trim().toLowerCase();
        String filtroEstado = comboFiltroEstado.getValue() == null ? FILTRO_TODOS : comboFiltroEstado.getValue();

        productosFiltrados.setPredicate(producto -> {
            boolean coincideTexto = filtroTexto.isEmpty()
                    || producto.getNombre().toLowerCase().contains(filtroTexto)
                    || producto.getCodigo().toLowerCase().contains(filtroTexto);
            boolean coincideEstado = switch (filtroEstado) {
                case FILTRO_ACTIVOS -> producto.isActivo();
                case FILTRO_INACTIVOS -> !producto.isActivo();
                default -> true;
            };
            return coincideTexto && coincideEstado;
        });
    }

    private void cargarTabla() {
        productosBase = FXCollections.observableArrayList(productoDAO.listarTodos());
        productosFiltrados = new FilteredList<>(productosBase, p -> true);
        tablaProductos.setItems(productosFiltrados);
        aplicarFiltros();
    }

    @FXML
    private void onSeleccionarProducto() {
        productoSeleccionado = tablaProductos.getSelectionModel().getSelectedItem();
        if (productoSeleccionado == null) {
            return;
        }
        campoCodigo.setText(productoSeleccionado.getCodigo());
        campoNombre.setText(productoSeleccionado.getNombre());
        campoPrecioMinorista.setText(productoSeleccionado.getPrecioKgMinorista().toPlainString());
        campoPrecioMayorista.setText(productoSeleccionado.getPrecioKgMayorista().toPlainString());
        campoStock.setText(productoSeleccionado.getStockDisponible().toPlainString());
        campoImagenPath.setText(productoSeleccionado.getImagenPath() == null ? "" : productoSeleccionado.getImagenPath());
        checkActivo.setSelected(productoSeleccionado.isActivo());

        labelLoteActual.setText(productoSeleccionado.getLoteActual() == null ? "-" : productoSeleccionado.getLoteActual());
        labelFechaProduccionActual.setText(productoSeleccionado.getFechaProduccionActual() == null
                ? "-" : productoSeleccionado.getFechaProduccionActual().format(FORMATO_FECHA));
        labelFechaVencimientoActual.setText(productoSeleccionado.getFechaVencimientoActual() == null
                ? "-" : productoSeleccionado.getFechaVencimientoActual().format(FORMATO_FECHA));
    }

    @FXML
    private void onNuevoProducto() {
        productoSeleccionado = null;
        tablaProductos.getSelectionModel().clearSelection();
        campoCodigo.clear();
        campoNombre.clear();
        campoPrecioMinorista.clear();
        campoPrecioMayorista.clear();
        campoStock.setText("0");
        campoImagenPath.clear();
        checkActivo.setSelected(true);
        labelLoteActual.setText("-");
        labelFechaProduccionActual.setText("-");
        labelFechaVencimientoActual.setText("-");
    }

    @FXML
    private void onExaminarImagen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar imagen del producto");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg"));
        var archivo = chooser.showOpenDialog(tablaProductos.getScene().getWindow());
        if (archivo == null) {
            return;
        }
        try {
            String rutaRelativa = copiarImagenACarpetaProductos(archivo.toPath());
            campoImagenPath.setText(rutaRelativa);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "No se pudo copiar la imagen a " + CARPETA_IMAGENES_PRODUCTOS
                    + ": " + e.getMessage()).showAndWait();
        }
    }

    /**
     * Copia la imagen elegida a {@value #CARPETA_IMAGENES_PRODUCTOS} (relativa a la carpeta
     * del proyecto/ejecutable) para que la ruta guardada en la base de datos sea portable
     * entre equipos, en lugar de la ruta absoluta del archivo original.
     */
    private String copiarImagenACarpetaProductos(Path origen) throws IOException {
        Files.createDirectories(CARPETA_IMAGENES_PRODUCTOS);
        Path destino = CARPETA_IMAGENES_PRODUCTOS.resolve(origen.getFileName());
        if (!origen.toAbsolutePath().normalize().equals(destino.toAbsolutePath().normalize())) {
            Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        return destino.toString().replace('\\', '/');
    }

    @FXML
    private void onGuardarProducto() {
        String codigo = campoCodigo.getText() == null ? "" : campoCodigo.getText().trim();
        String nombre = campoNombre.getText() == null ? "" : campoNombre.getText().trim();
        if (codigo.isEmpty() || nombre.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Ingrese codigo y nombre del producto").showAndWait();
            return;
        }

        BigDecimal precioMinorista = parsearDecimal(campoPrecioMinorista.getText());
        BigDecimal precioMayorista = parsearDecimal(campoPrecioMayorista.getText());
        BigDecimal stock = parsearDecimal(campoStock.getText());
        if (precioMinorista == null || precioMayorista == null || stock == null) {
            new Alert(Alert.AlertType.WARNING, "Precios y stock deben ser numeros validos").showAndWait();
            return;
        }
        if (precioMinorista.signum() < 0 || precioMayorista.signum() < 0 || stock.signum() < 0) {
            new Alert(Alert.AlertType.WARNING, "Precios y stock no pueden ser negativos").showAndWait();
            return;
        }

        Producto producto = productoSeleccionado != null ? productoSeleccionado : new Producto();
        producto.setCodigo(codigo);
        producto.setNombre(nombre);
        producto.setPrecioKgMinorista(precioMinorista);
        producto.setPrecioKgMayorista(precioMayorista);
        producto.setStockDisponible(stock);
        producto.setUnidadMedida(producto.getUnidadMedida() == null ? "kg" : producto.getUnidadMedida());
        producto.setImagenPath(campoImagenPath.getText() == null || campoImagenPath.getText().isBlank()
                ? null : campoImagenPath.getText().trim());
        producto.setActivo(checkActivo.isSelected());

        try {
            if (productoSeleccionado != null) {
                productoDAO.actualizar(producto);
            } else {
                productoDAO.guardar(producto);
            }
        } catch (RuntimeException e) {
            new Alert(Alert.AlertType.ERROR, "No se pudo guardar el producto: " + e.getMessage()).showAndWait();
            return;
        }

        cargarTabla();
        onNuevoProducto();
    }

    @FXML
    private void onDesactivarProducto() {
        if (productoSeleccionado == null) {
            new Alert(Alert.AlertType.WARNING, "Seleccione un producto de la tabla").showAndWait();
            return;
        }
        productoSeleccionado.setActivo(false);
        productoDAO.actualizar(productoSeleccionado);
        cargarTabla();
        onNuevoProducto();
    }

    private BigDecimal parsearDecimal(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

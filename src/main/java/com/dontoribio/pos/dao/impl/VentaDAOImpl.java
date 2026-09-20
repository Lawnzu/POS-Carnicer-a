package com.dontoribio.pos.dao.impl;

import com.dontoribio.pos.config.DatabaseConnection;
import com.dontoribio.pos.dao.VentaDAO;
import com.dontoribio.pos.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion JDBC de {@link VentaDAO}. Inserta cabecera, detalles y pagos
 * dentro de una unica transaccion (commit/rollback manual sobre la conexion).
 * Esquema esperado: ventas, detalle_ventas, pagos (FK venta_id).
 */
public class VentaDAOImpl implements VentaDAO {

    @Override
    public Venta registrarVenta(Venta venta) {
        String sqlVenta = "INSERT INTO ventas (fecha, cliente_id, usuario_id, subtotal, total) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING id";
        String sqlDetalle = "INSERT INTO detalle_ventas (venta_id, producto_id, peso_kg, precio_unitario, importe) " +
                "VALUES (?, ?, ?, ?, ?)";
        String sqlPago = "INSERT INTO pagos (venta_id, metodo_pago, monto, referencia) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDateTime fecha = venta.getFecha() != null ? venta.getFecha() : LocalDateTime.now();

                try (PreparedStatement ps = conn.prepareStatement(sqlVenta)) {
                    ps.setTimestamp(1, Timestamp.valueOf(fecha));
                    ps.setLong(2, venta.getCliente().getId());
                    ps.setLong(3, venta.getUsuario().getId());
                    ps.setBigDecimal(4, venta.getSubtotal());
                    ps.setBigDecimal(5, venta.getTotal());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            venta.setId(rs.getLong("id"));
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlDetalle)) {
                    for (DetalleVenta detalle : venta.getDetalles()) {
                        ps.setLong(1, venta.getId());
                        ps.setLong(2, detalle.getProducto().getId());
                        ps.setBigDecimal(3, detalle.getPesoKg());
                        ps.setBigDecimal(4, detalle.getPrecioUnitario());
                        ps.setBigDecimal(5, detalle.getImporte());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlPago)) {
                    for (Pago pago : venta.getPagos()) {
                        ps.setLong(1, venta.getId());
                        ps.setString(2, pago.getMetodoPago().name());
                        ps.setBigDecimal(3, pago.getMonto());
                        ps.setString(4, pago.getReferencia());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                venta.setFecha(fecha);
                return venta;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Error al registrar venta, se revirtio la transaccion", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error de conexion al registrar venta", e);
        }
    }

    @Override
    public Optional<Venta> buscarPorId(Long id) {
        String sql = "SELECT id, fecha, subtotal, total FROM ventas WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Venta venta = new Venta();
                venta.setId(rs.getLong("id"));
                venta.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                return Optional.of(venta);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar venta por id", e);
        }
    }

    @Override
    public List<Venta> listarPorFecha(LocalDate fecha) {
        String sql = "SELECT id, fecha, subtotal, total FROM ventas WHERE fecha::date = ? ORDER BY fecha DESC";
        List<Venta> ventas = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Venta venta = new Venta();
                    venta.setId(rs.getLong("id"));
                    venta.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                    ventas.add(venta);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar ventas por fecha", e);
        }
        return ventas;
    }
}

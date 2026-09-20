package com.dontoribio.pos.dao.impl;

import com.dontoribio.pos.config.DatabaseConnection;
import com.dontoribio.pos.dao.ProductoDAO;
import com.dontoribio.pos.model.Producto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion JDBC de {@link ProductoDAO} contra la tabla `productos`
 * (esquema esperado: id, codigo, nombre, precio_kg_minorista, precio_kg_mayorista,
 * unidad_medida, stock_disponible, activo).
 */
public class ProductoDAOImpl implements ProductoDAO {

    @Override
    public Optional<Producto> buscarPorId(Long id) {
        String sql = "SELECT * FROM productos WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar producto por id", e);
        }
    }

    @Override
    public Optional<Producto> buscarPorCodigo(String codigo) {
        String sql = "SELECT * FROM productos WHERE codigo = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar producto por codigo", e);
        }
    }

    @Override
    public List<Producto> listarActivos() {
        String sql = "SELECT * FROM productos WHERE activo = TRUE ORDER BY nombre";
        List<Producto> productos = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                productos.add(mapear(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar productos activos", e);
        }
        return productos;
    }

    @Override
    public Producto guardar(Producto producto) {
        String sql = "INSERT INTO productos (codigo, nombre, precio_kg_minorista, precio_kg_mayorista, " +
                "unidad_medida, stock_disponible, activo) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, producto.getCodigo());
            ps.setString(2, producto.getNombre());
            ps.setBigDecimal(3, producto.getPrecioKgMinorista());
            ps.setBigDecimal(4, producto.getPrecioKgMayorista());
            ps.setString(5, producto.getUnidadMedida());
            ps.setBigDecimal(6, producto.getStockDisponible());
            ps.setBoolean(7, producto.isActivo());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    producto.setId(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar producto", e);
        }
        return producto;
    }

    @Override
    public void actualizar(Producto producto) {
        String sql = "UPDATE productos SET codigo = ?, nombre = ?, precio_kg_minorista = ?, " +
                "precio_kg_mayorista = ?, unidad_medida = ?, stock_disponible = ?, activo = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, producto.getCodigo());
            ps.setString(2, producto.getNombre());
            ps.setBigDecimal(3, producto.getPrecioKgMinorista());
            ps.setBigDecimal(4, producto.getPrecioKgMayorista());
            ps.setString(5, producto.getUnidadMedida());
            ps.setBigDecimal(6, producto.getStockDisponible());
            ps.setBoolean(7, producto.isActivo());
            ps.setLong(8, producto.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar producto", e);
        }
    }

    @Override
    public void eliminar(Long id) {
        String sql = "DELETE FROM productos WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar producto", e);
        }
    }

    private Producto mapear(ResultSet rs) throws SQLException {
        Producto producto = new Producto();
        producto.setId(rs.getLong("id"));
        producto.setCodigo(rs.getString("codigo"));
        producto.setNombre(rs.getString("nombre"));
        producto.setPrecioKgMinorista(rs.getBigDecimal("precio_kg_minorista"));
        producto.setPrecioKgMayorista(rs.getBigDecimal("precio_kg_mayorista"));
        producto.setUnidadMedida(rs.getString("unidad_medida"));
        producto.setStockDisponible(rs.getBigDecimal("stock_disponible"));
        producto.setActivo(rs.getBoolean("activo"));
        return producto;
    }
}

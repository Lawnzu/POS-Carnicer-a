package com.dontoribio.pos.dao;

import com.dontoribio.pos.model.Producto;

import java.util.List;
import java.util.Optional;

/** Operaciones de acceso a datos para el catalogo de productos carnicos. */
public interface ProductoDAO {

    Optional<Producto> buscarPorId(Long id);

    Optional<Producto> buscarPorCodigo(String codigo);

    List<Producto> listarActivos();

    Producto guardar(Producto producto);

    void actualizar(Producto producto);

    void eliminar(Long id);
}

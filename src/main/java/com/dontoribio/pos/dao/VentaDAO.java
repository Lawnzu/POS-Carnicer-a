package com.dontoribio.pos.dao;

import com.dontoribio.pos.model.Venta;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistencia de ventas junto con sus detalles y pagos (operacion transaccional). */
public interface VentaDAO {

    /** Inserta la venta, sus detalles y pagos dentro de una unica transaccion. */
    Venta registrarVenta(Venta venta);

    Optional<Venta> buscarPorId(Long id);

    List<Venta> listarPorFecha(LocalDate fecha);
}

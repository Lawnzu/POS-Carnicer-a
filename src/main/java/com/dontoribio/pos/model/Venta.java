package com.dontoribio.pos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Venta consolidada a partir del cierre de una lista de atencion. */
public class Venta {

    private Long id;
    private LocalDateTime fecha;
    private Cliente cliente;
    private Usuario usuario;
    private List<DetalleVenta> detalles = new ArrayList<>();
    private List<Pago> pagos = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    public Venta() {
    }

    public void agregarDetalle(DetalleVenta detalle) {
        detalles.add(detalle);
        recalcularTotales();
    }

    public void agregarPago(Pago pago) {
        pagos.add(pago);
    }

    private void recalcularTotales() {
        subtotal = detalles.stream()
                .map(DetalleVenta::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        total = subtotal; // MVP: sin descuentos/impuestos adicionales
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public List<DetalleVenta> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetalleVenta> detalles) {
        this.detalles = detalles;
        recalcularTotales();
    }

    public List<Pago> getPagos() {
        return pagos;
    }

    public void setPagos(List<Pago> pagos) {
        this.pagos = pagos;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }
}

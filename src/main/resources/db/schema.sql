-- Esquema minimo requerido por los DAO JDBC del MVP.
-- Ejecutar contra la base "dontoribio_pos" (ver application.properties).

CREATE TABLE IF NOT EXISTS usuarios (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) UNIQUE NOT NULL,
    password_hash   VARCHAR(60) NOT NULL,
    nombre_completo VARCHAR(150) NOT NULL,
    rol             VARCHAR(20) NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'CAJERO')),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS clientes (
    id                  BIGSERIAL PRIMARY KEY,
    tipo_cliente        VARCHAR(20) NOT NULL CHECK (tipo_cliente IN ('MINORISTA', 'MAYORISTA')),
    nombre_razon_social VARCHAR(150) NOT NULL,
    documento           VARCHAR(20) UNIQUE,
    telefono            VARCHAR(20),
    direccion           VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS productos (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(30) UNIQUE NOT NULL,
    nombre              VARCHAR(150) NOT NULL,
    precio_kg_minorista NUMERIC(10, 2) NOT NULL,
    precio_kg_mayorista NUMERIC(10, 2) NOT NULL,
    unidad_medida       VARCHAR(10) NOT NULL DEFAULT 'kg',
    stock_disponible    NUMERIC(10, 3) NOT NULL DEFAULT 0,
    activo              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS ventas (
    id          BIGSERIAL PRIMARY KEY,
    fecha       TIMESTAMP NOT NULL DEFAULT NOW(),
    cliente_id  BIGINT REFERENCES clientes (id),
    usuario_id  BIGINT NOT NULL REFERENCES usuarios (id),
    subtotal    NUMERIC(10, 2) NOT NULL,
    total       NUMERIC(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS detalle_ventas (
    id              BIGSERIAL PRIMARY KEY,
    venta_id        BIGINT NOT NULL REFERENCES ventas (id) ON DELETE CASCADE,
    producto_id     BIGINT NOT NULL REFERENCES productos (id),
    peso_kg         NUMERIC(10, 3) NOT NULL,
    precio_unitario NUMERIC(10, 2) NOT NULL,
    importe         NUMERIC(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS pagos (
    id          BIGSERIAL PRIMARY KEY,
    venta_id    BIGINT NOT NULL REFERENCES ventas (id) ON DELETE CASCADE,
    metodo_pago VARCHAR(20) NOT NULL CHECK (metodo_pago IN ('EFECTIVO', 'YAPE', 'PLIN', 'TARJETA', 'TRANSFERENCIA')),
    monto       NUMERIC(10, 2) NOT NULL,
    referencia  VARCHAR(50)
);

INSERT INTO usuarios(username, password_hash, nombre_completo, rol, activo) VALUES ('DonToribio', 'Palomino12/', 'Administrador', 'ADMINISTRADOR', true);
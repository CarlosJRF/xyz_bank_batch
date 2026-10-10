-- Esquema de las tablas de negocio. Se ejecuta en cada arranque (spring.sql.init.mode=always).
-- Si existían tablas creadas con una versión anterior del esquema, eliminarlas una vez:
--   DROP TABLE IF EXISTS transaction_report, account, annual_statement;

--Tabla para registrar las transacciones validadas
CREATE TABLE IF NOT EXISTS transaction_report(
    id SERIAL PRIMARY KEY,
    source_id INTEGER NOT NULL UNIQUE,
    transaction_date DATE NOT NULL,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    operation_type VARCHAR(20) NOT NULL CHECK (operation_type IN ('DEBITO', 'CREDITO'))
);

--Tabla para gestionar las cuentas y los saldos con el interés aplicado
CREATE TABLE IF NOT EXISTS account (
    id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL,
    client_name VARCHAR(100) NOT NULL,
    balance NUMERIC(15, 2) NOT NULL CHECK (balance >= 0),
    interest NUMERIC(15, 2) NOT NULL,
    age INTEGER NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('AHORRO', 'PRESTAMO', 'HIPOTECA'))
);

--Tabla para almacenar los estados de cuenta anuales
CREATE TABLE IF NOT EXISTS annual_statement (
    id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL,
    statement_date DATE NOT NULL,
    transaction VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255) NOT NULL
);

--Bitácora de calidad de datos: registros rechazados y correcciones aplicadas
CREATE TABLE IF NOT EXISTS data_quality_log (
    id SERIAL PRIMARY KEY,
    step_name VARCHAR(50) NOT NULL,
    line_number BIGINT,
    action VARCHAR(20) NOT NULL,
    error_code VARCHAR(50) NOT NULL,
    error_detail TEXT,
    raw_record TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

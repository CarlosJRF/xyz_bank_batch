--Tabla para registrar las transaccciones

CREATE TABLE IF NOT EXISTS transaction_report(
    id SERIAL PRIMARY KEY,
    transaction_date DATE,
    amount NUMERIC (10),
    operation_type VARCHAR(20)
);

--Tabla para gestionar las cuentas y los saldos

CREATE TABLE IF NOT EXISTS account (
	id SERIAL PRIMARY KEY,
	client_name VARCHAR(100) NOT NULL,
	balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
	age VARCHAR(3),
	account_type VARCHAR(20) NOT NULL
	
);

--Tabla para almacenar los estados de cuenta anuales
CREATE TABLE IF NOT EXISTS annual_statement (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL,
    statement_date DATE NOT NULL,
    transaction VARCHAR(50),
    amount NUMERIC(15, 2) NOT NULL,
    description VARCHAR(255)
);
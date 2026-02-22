-- ============================================================
-- FIX: Ampliar hashPassword a VARCHAR(255)
-- bcrypt genera hashes de 60 caracteres; VARCHAR(50) los trunca
-- causando que el INSERT falle y el registro/login no funcionen.
-- ============================================================

-- 1. Ampliar la columna
ALTER TABLE Usuario ALTER COLUMN hashPassword VARCHAR(255);

-- 2. Verificar resultado
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'Usuario' AND COLUMN_NAME = 'hashPassword';

-- 3. (Opcional) Verificar que existan los roles base
--    Si la tabla Rol está vacía, ejecutar también:
-- INSERT INTO Rol (nombre) VALUES ('Administrador');
-- INSERT INTO Rol (nombre) VALUES ('Operativo');
-- INSERT INTO Rol (nombre) VALUES ('Analista');

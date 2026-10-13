CREATE DATABASE IF NOT EXISTS dorm_laundry
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'laundry_app'@'localhost'
    IDENTIFIED BY 'laundry_local_dev';
CREATE USER IF NOT EXISTS 'laundry_app'@'127.0.0.1'
    IDENTIFIED BY 'laundry_local_dev';

GRANT ALL PRIVILEGES ON dorm_laundry.* TO 'laundry_app'@'localhost';
GRANT ALL PRIVILEGES ON dorm_laundry.* TO 'laundry_app'@'127.0.0.1';
FLUSH PRIVILEGES;

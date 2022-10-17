CREATE TABLE int_lock  (
						   lock_key CHAR(36) NOT NULL,
						   region VARCHAR(100) NOT NULL,
						   client_id CHAR(36),
						   created_date DATETIME(6) NOT NULL,
						   constraint INT_LOCK_PK primary key (lock_key, region)
) ENGINE=InnoDB;


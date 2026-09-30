--liquibase formatted sql

--changeset l2jfree:1
CREATE TABLE liquibase_compatibility_probe (
	id INT NOT NULL PRIMARY KEY,
	value_text VARCHAR(64) NOT NULL
);

--changeset l2jfree:2
ALTER TABLE liquibase_compatibility_probe
	ADD COLUMN applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- The stand profile: the read-only role simulator_ro.
--
-- It may connect to the current database and read five columns of world.player:
-- id, name, account_name, exp, sp. It has no other privilege: no other column or
-- table, no write, no DDL, and every session starts read-only. Run it as the
-- superuser after the server has migrated the world schema; it can run again.
-- The password is not set here (see readonly-role.sh).
DO $$
BEGIN
	IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'simulator_ro') THEN
		CREATE ROLE simulator_ro LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
	END IF;
	EXECUTE format('GRANT CONNECT ON DATABASE %I TO simulator_ro', current_database());
END
$$;
ALTER ROLE simulator_ro SET default_transaction_read_only = on;
GRANT USAGE ON SCHEMA world TO simulator_ro;
GRANT SELECT (id, name, account_name, exp, sp) ON world.player TO simulator_ro;

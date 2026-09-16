-- Run once against the existing development PostgreSQL database before restarting
-- the updated backend. Safe to rerun. Fresh installations use Hibernate's schema.
BEGIN;
DO $$
DECLARE
    status_column smallint;
    existing_constraint record;
BEGIN
    IF to_regclass('appointments') IS NOT NULL THEN
        SELECT attnum INTO status_column FROM pg_attribute
        WHERE attrelid = 'appointments'::regclass AND attname = 'status';
        -- Hibernate's generated name can differ between databases. Replace only
        -- single-column CHECK constraints on the appointment status column.
        FOR existing_constraint IN
            SELECT conname FROM pg_constraint
            WHERE conrelid = 'appointments'::regclass AND contype = 'c'
              AND conkey = ARRAY[status_column]::smallint[]
        LOOP
            EXECUTE format('ALTER TABLE appointments DROP CONSTRAINT %I', existing_constraint.conname);
        END LOOP;
        ALTER TABLE appointments ADD CONSTRAINT appointments_status_check
            CHECK (status IN ('BOOKED','ARRIVED','IN_CONSULTATION','COMPLETED','CANCELLED','NO_SHOW'));
        ALTER TABLE appointments ADD COLUMN IF NOT EXISTS checked_in_at timestamptz;
        ALTER TABLE appointments ADD COLUMN IF NOT EXISTS consultation_started_at timestamptz;
        ALTER TABLE appointments ADD COLUMN IF NOT EXISTS checked_out_at timestamptz;
        ALTER TABLE appointments ADD COLUMN IF NOT EXISTS follow_up_for_id bigint;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'appointments'::regclass
                       AND conname = 'fk_appointment_follow_up') THEN
            ALTER TABLE appointments ADD CONSTRAINT fk_appointment_follow_up
                FOREIGN KEY (follow_up_for_id) REFERENCES appointments(id);
        END IF;
    END IF;
END $$;
COMMIT;

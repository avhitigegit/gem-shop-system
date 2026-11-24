-- P6-01: two-step sign-in for the owner (Security & Data Agreement, step 4): a 6-digit code from an authenticator app
-- (Google / Microsoft Authenticator) after the password. Backup codes for a lost phone.
alter table app_user add column two_step_on        boolean not null default false;
alter table app_user add column two_step_secret    text;          -- encrypted with ENCRYPTION_KEY
alter table app_user add column two_step_last_step bigint;        -- last 30-second step used: the same code works only once
alter table app_user add column two_step_backup    text;          -- SHA-256 of the unused backup codes, comma separated
alter table app_user add column two_step_on_at     timestamptz;

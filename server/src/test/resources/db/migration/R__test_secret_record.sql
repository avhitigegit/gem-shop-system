-- Test only: a table with encrypted passport / phone / email and a search key, like the customers table (P4-03).
create table if not exists test_secret_record (
    id          bigint generated always as identity primary key,
    passport    text,
    phone       text,
    email       text,
    email_index varchar(64),
    created_at  timestamptz not null,
    created_by  varchar(64) not null,
    updated_at  timestamptz not null,
    updated_by  varchar(64) not null,
    version     bigint      not null default 0
);

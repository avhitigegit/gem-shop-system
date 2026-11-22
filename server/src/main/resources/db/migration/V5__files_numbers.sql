-- P1-05: uploaded files (photos, bill photos, certificate scans, logo) and running numbers without gaps.

-- One uploaded file. The bytes are on disk (Docker volume, nightly copy to S3 – P6-04); this row is the record.
-- A file is "attached" once the gem / expense / … it belongs to is saved; unattached uploads are removed after a day.
create table stored_file (
    id            bigint generated always as identity primary key,
    public_id     uuid         not null unique,       -- used in links (cannot be guessed)
    kind          varchar(30)  not null check (kind in ('PRODUCT_PHOTO', 'BILL_PHOTO', 'CERTIFICATE', 'LOGO')),
    content_type  varchar(60)  not null,
    size_bytes    bigint       not null check (size_bytes > 0),
    original_name varchar(255) not null,
    storage_key   varchar(255) not null unique,
    thumb_key     varchar(255) unique,                -- small picture for lists (images only)
    width         integer,
    height        integer,
    branch_id     bigint       references branch (id),  -- empty = whole shop
    uploaded_by   bigint       references app_user (id),
    owner_type    varchar(40),                        -- e.g. GEM, EXPENSE – empty until attached
    owner_ref     varchar(40),                        -- e.g. GEM-00131
    created_at    timestamptz  not null,
    created_by    varchar(64)  not null,
    updated_at    timestamptz  not null,
    updated_by    varchar(64)  not null,
    version       bigint       not null default 0,
    constraint ck_stored_file_owner check ((owner_type is null) = (owner_ref is null))
);
create index ix_stored_file_owner on stored_file (owner_type, owner_ref);
create index ix_stored_file_unattached on stored_file (created_at) where owner_type is null;

-- Running numbers without gaps (BRD rule 4): GEM-00001, INV-000001, CN-0001, …
-- The row is locked while a document is saved, so two sales at once never get the same number,
-- and a sale that fails gives its number back (no gap).
create table number_counter (
    id         bigint generated always as identity primary key,
    name       varchar(30) not null unique,
    prefix     varchar(10) not null,
    digits     integer     not null check (digits between 3 and 10),
    last_value bigint      not null default 0 check (last_value >= 0),
    created_at timestamptz not null default now(),
    created_by varchar(64) not null default 'system',
    updated_at timestamptz not null default now(),
    updated_by varchar(64) not null default 'system',
    version    bigint      not null default 0
);

insert into number_counter (name, prefix, digits) values
    ('GEM',              'GEM-', 5),
    ('INVOICE',          'INV-', 6),
    ('CREDIT_NOTE',      'CN-',  4),
    ('PURCHASE',         'PUR-', 4),
    ('QUOTE',            'QT-',  4),
    ('RESERVATION',      'RS-',  4),
    ('HELD_SALE',        'HS-',  4),
    ('EXPENSE',          'EXP-', 4),
    ('TRANSFER',         'TR-',  4),
    ('RETURN',           'RT-',  4),
    ('STOCK_CORRECTION', 'SC-',  4),
    ('SUPPLIER_RETURN',  'SR-',  4),
    ('STOCK_CHECK',      'SCK-', 4);

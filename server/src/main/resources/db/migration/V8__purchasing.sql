-- P2-03 Suppliers and purchases (BRD B1–B4). Purchases are paid in cash; the supplier's bill photo is a stored file
-- (owner_type 'PURCHASE', owner_ref = purchase number). Gems link back with gem.supplier_code / gem.purchase_no.

create table supplier (
    id         bigint generated always as identity primary key,
    code       varchar(12)  not null unique,                    -- SUP-01
    name       varchar(120) not null,
    contact    varchar(120),
    phone      varchar(40)  not null,
    address    varchar(300) not null,
    id_type    varchar(4)   not null check (id_type in ('NIC', 'BR')), -- NIC (person) or business registration
    id_no      text         not null,                           -- encrypted (EncryptedString)
    notes      varchar(500),
    created_at timestamptz  not null,
    created_by varchar(64)  not null,
    updated_at timestamptz  not null,
    updated_by varchar(64)  not null,
    version    bigint       not null default 0
);

create table purchase (
    id            bigint generated always as identity primary key,
    number        varchar(20)   not null unique,                -- PUR-0015
    purchase_date date          not null,
    supplier_id   bigint        not null references supplier (id),
    branch_id     bigint        not null references branch (id),
    total         numeric(14,2) not null check (total >= 0),
    paid_by       varchar(10)   not null default 'CASH' check (paid_by in ('CASH')),
    bill_photo    uuid,                                         -- stored_file.public_id (required on new purchases)
    notes         varchar(500),
    recorded_by   bigint        references app_user (id),
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0
);
create index ix_purchase_date on purchase (purchase_date desc);
create index ix_purchase_supplier on purchase (supplier_id, purchase_date desc);

create table purchase_line (
    id          bigint generated always as identity primary key,
    purchase_id bigint        not null references purchase (id),
    line_no     int           not null,
    gem_id      bigint        not null unique references gem (id), -- each gem is bought once
    cost        numeric(14,2) not null check (cost >= 0),
    created_at  timestamptz   not null,
    created_by  varchar(64)   not null,
    updated_at  timestamptz   not null,
    updated_by  varchar(64)   not null,
    version     bigint        not null default 0,
    unique (purchase_id, line_no)
);

create table supplier_return (
    id            bigint generated always as identity primary key,
    number        varchar(20)   not null unique,                -- SR-0002
    returned_at   timestamptz   not null,
    supplier_id   bigint        not null references supplier (id),
    gem_id        bigint        not null unique references gem (id),
    reason        varchar(500)  not null,
    refund        numeric(14,2) not null check (refund >= 0),
    refund_method varchar(12)   not null check (refund_method in ('CASH', 'REPLACEMENT', 'CREDIT')),
    recorded_by   bigint        references app_user (id),
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0
);
create index ix_supplier_return_supplier on supplier_return (supplier_id, returned_at desc);

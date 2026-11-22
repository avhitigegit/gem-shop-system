-- P2-01 Gems (BRD C1–C9, D1, D2): every gem product is unique, with its own code, photos and certificate.
-- Photos and the certificate scan are stored files (stored_file, owner_type 'GEM', owner_ref = gem code).

create table gem (
    id            bigint generated always as identity primary key,
    code          varchar(12)   not null unique,               -- GEM-00131 (running number, never reused)
    name          varchar(120)  not null,
    product_type  varchar(80)   not null,                      -- drop-down lists (Settings)
    gem_type      varchar(80)   not null,
    carat         numeric(8,2)  not null check (carat > 0),
    colour        varchar(80)   not null,
    shape         varchar(80)   not null,
    treatment     varchar(80)   not null,
    description   varchar(1000),
    cert_type     varchar(10)   not null check (cert_type in ('LAB', 'SHOP', 'NONE')),
    cert_lab      varchar(80),
    cert_no       varchar(60),
    cert_date     date,
    cert_scan     uuid,                                        -- stored_file.public_id
    cost          numeric(14,2) not null check (cost >= 0),    -- owner only
    price         numeric(14,2) not null check (price > 0),
    min_price     numeric(14,2) not null check (min_price > 0),
    branch_id     bigint        not null references branch (id),
    status        varchar(20)   not null check (status in ('IN_STOCK', 'NOT_READY', 'RESERVED', 'ON_HOLD', 'SOLD',
                      'RETURNED', 'IN_TRANSIT', 'WRITTEN_OFF', 'RETURNED_SUPPLIER')),
    supplier_code varchar(20),                                 -- linked by purchases (P2-03)
    purchase_no   varchar(20),
    sale_no       varchar(20),                                 -- invoice that sold it (P3)
    sold_at       timestamptz,
    added_at      timestamptz   not null,
    added_by      bigint        references app_user (id),
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0,
    -- rule 2: minimum price between cost and selling price
    constraint ck_gem_prices check (min_price <= price and min_price >= cost),
    constraint ck_gem_lab_cert check (cert_type <> 'LAB' or (cert_lab is not null and cert_no is not null))
);
create index ix_gem_branch_status on gem (branch_id, status);
create index ix_gem_gem_type on gem (gem_type);
create index ix_gem_product_type on gem (product_type);

-- What happened to one gem (added, edited, price changed, ready to sell, sold, moved …), newest last.
create table gem_history (
    id         bigint generated always as identity primary key,
    gem_id     bigint        not null references gem (id),
    at         timestamptz   not null,
    user_id    bigint        references app_user (id),
    user_name  varchar(120)  not null,
    action     varchar(80)   not null,
    detail     varchar(500)  not null default '',
    created_at timestamptz   not null,
    created_by varchar(64)   not null,
    updated_at timestamptz   not null,
    updated_by varchar(64)   not null,
    version    bigint        not null default 0
);
create index ix_gem_history_gem on gem_history (gem_id, at);

-- P3-01 New sale (BRD F1–F6, G1–G2, H1–H3, J2): customers, guides / drivers, sales (invoice), held sales.

-- Customers (F5, J2). Screens for customers come with P4-03; the sale adds them.
create table customer (
    id            bigint generated always as identity primary key,
    code          varchar(12)  not null unique,                  -- C-026
    name          varchar(120) not null,
    country       varchar(60)  not null,
    email         varchar(160),
    whatsapp      varchar(40),
    birthday      varchar(5)   check (birthday ~ '^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$'), -- MM-DD, no year
    consent       boolean      not null default false,           -- promotions by email / WhatsApp (PDPA)
    consent_at    timestamptz,
    unsubscribed  boolean      not null default false,
    passport      text,                                          -- encrypted
    notes         varchar(500),
    joined_at     timestamptz  not null,
    created_at    timestamptz  not null,
    created_by    varchar(64)  not null,
    updated_at    timestamptz  not null,
    updated_by    varchar(64)  not null,
    version       bigint       not null default 0
);
create index ix_customer_name on customer (lower(name));

-- Guides and drivers who bring customers (F4, H1). Screens come with P3-05.
create table guide (
    id          bigint generated always as identity primary key,
    code        varchar(12)  not null unique,                    -- G-07
    name        varchar(120) not null,
    vehicle     varchar(40),
    vehicle_no  varchar(20),
    phone       varchar(40)  not null,
    pct         numeric(5,2) not null check (pct between 0 and 50),
    since       date,
    active      boolean      not null default true,
    created_at  timestamptz  not null,
    created_by  varchar(64)  not null,
    updated_at  timestamptz  not null,
    updated_by  varchar(64)  not null,
    version     bigint       not null default 0
);

-- One sale = one invoice (F6: many gems per bill). Prices in LKR; VAT is inside the price (no VAT line).
create table sale (
    id            bigint generated always as identity primary key,
    number        varchar(20)   not null unique,                 -- INV-000141
    sold_at       timestamptz   not null,
    branch_id     bigint        not null references branch (id),
    customer_id   bigint        not null references customer (id),
    guide_id      bigint        references guide (id),           -- null = walk-in
    staff_id      bigint        references app_user (id),
    status        varchar(12)   not null check (status in ('COMPLETED', 'CANCELLED')),
    total         numeric(14,2) not null check (total > 0),
    guide_pct     numeric(5,2)  not null default 0,
    guide_comm    numeric(14,2) not null default 0,
    staff_comm    numeric(14,2) not null default 0,
    card_fee      numeric(14,2) not null default 0,              -- paid by the shop (bank), not by the customer
    change_due    numeric(14,2) not null default 0,
    rate_usd      numeric(12,4),                                 -- today's rates used for this sale
    rate_eur      numeric(12,4),
    held_no       varchar(20),
    quote_no      varchar(20),
    sent_email    boolean       not null default false,
    sent_whatsapp boolean       not null default false,
    notes         varchar(500),
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0
);
create index ix_sale_sold on sale (sold_at desc);
create index ix_sale_customer on sale (customer_id);

create table sale_item (
    id           bigint generated always as identity primary key,
    sale_id      bigint        not null references sale (id),
    line_no      int           not null,
    gem_id       bigint        not null references gem (id),
    list_price   numeric(14,2) not null,
    agreed       numeric(14,2) not null check (agreed > 0),
    min_price    numeric(14,2),
    approval_no  varchar(20),                                    -- below-minimum approval
    released     boolean       not null default false,           -- returned / invoice cancelled: the gem may be sold again
    created_at   timestamptz   not null,
    created_by   varchar(64)   not null,
    updated_at   timestamptz   not null,
    updated_by   varchar(64)   not null,
    version      bigint        not null default 0,
    unique (sale_id, line_no)
);
-- Rule 3: a gem is on one live sale only.
create unique index ux_sale_item_gem on sale_item (gem_id) where not released;

create table sale_payment (
    id          bigint generated always as identity primary key,
    sale_id     bigint        not null references sale (id),
    line_no     int           not null,
    method      varchar(4)    not null check (method in ('CASH', 'CARD')),
    currency    varchar(3)    not null check (currency in ('LKR', 'USD', 'EUR')),
    amount      numeric(14,2) not null check (amount > 0),
    rate        numeric(12,4) not null,
    lkr         numeric(14,2) not null,
    fee_pct     numeric(5,2)  not null default 0,
    fee         numeric(14,2) not null default 0,
    created_at  timestamptz   not null,
    created_by  varchar(64)   not null,
    updated_at  timestamptz   not null,
    updated_by  varchar(64)   not null,
    version     bigint        not null default 0,
    unique (sale_id, line_no)
);

-- A sale below the minimum price waits for the owner (F3, rule 2). The gems are "On hold" meanwhile.
create table held_sale (
    id           bigint generated always as identity primary key,
    number       varchar(20)  not null unique,                   -- HS-0002
    held_at      timestamptz  not null,
    branch_id    bigint       not null references branch (id),
    staff_id     bigint       references app_user (id),
    customer_id  bigint       not null references customer (id),
    guide_id     bigint       references guide (id),
    status       varchar(12)  not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED')),
    approval_no  varchar(20),
    sale_no      varchar(20),
    created_at   timestamptz  not null,
    created_by   varchar(64)  not null,
    updated_at   timestamptz  not null,
    updated_by   varchar(64)  not null,
    version      bigint       not null default 0
);

create table held_sale_item (
    id          bigint generated always as identity primary key,
    held_id     bigint        not null references held_sale (id),
    line_no     int           not null,
    gem_id      bigint        not null references gem (id),
    list_price  numeric(14,2) not null,
    agreed      numeric(14,2) not null check (agreed > 0),
    min_price   numeric(14,2),
    created_at  timestamptz   not null,
    created_by  varchar(64)   not null,
    updated_at  timestamptz   not null,
    updated_by  varchar(64)   not null,
    version     bigint        not null default 0,
    unique (held_id, line_no)
);

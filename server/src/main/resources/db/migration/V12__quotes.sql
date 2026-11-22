-- P3-03 Keep a gem / give a quote (BRD F7, rule 3). A reservation keeps the gems for one customer until a date
-- (gems "Reserved"); a quotation is a price offer with an expiry date (the gems are not kept). Both can become a sale.
create table quote (
    id             bigint generated always as identity primary key,
    number         varchar(20)   not null unique,                 -- RS-0003 / QT-0003
    kind           varchar(12)   not null check (kind in ('RESERVATION', 'QUOTATION')),
    created_on     timestamptz   not null,
    branch_id      bigint        not null references branch (id),
    staff_id       bigint        references app_user (id),
    customer_id    bigint        not null references customer (id),
    expires        date          not null,
    deposit        numeric(14,2) not null default 0 check (deposit >= 0),
    status         varchar(12)   not null check (status in ('ACTIVE', 'EXPIRED', 'RELEASED', 'CONVERTED')),
    sale_no        varchar(20),
    sent_email     boolean       not null default false,
    sent_whatsapp  boolean       not null default false,
    notes          varchar(500),
    created_at     timestamptz   not null,
    created_by     varchar(64)   not null,
    updated_at     timestamptz   not null,
    updated_by     varchar(64)   not null,
    version        bigint        not null default 0
);
create index ix_quote_active on quote (status, expires);

create table quote_item (
    id          bigint generated always as identity primary key,
    quote_id    bigint        not null references quote (id),
    line_no     int           not null,
    gem_id      bigint        not null references gem (id),
    price       numeric(14,2) not null check (price > 0),
    created_at  timestamptz   not null,
    created_by  varchar(64)   not null,
    updated_at  timestamptz   not null,
    updated_by  varchar(64)   not null,
    version     bigint        not null default 0,
    unique (quote_id, line_no)
);

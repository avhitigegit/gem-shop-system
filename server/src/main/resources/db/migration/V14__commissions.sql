-- P3-05 Guides and commission (BRD H1–H5, I2; rule 5).
-- Every sale writes one commission line per person who earns on it: the guide / driver (their % of the sale) and the
-- salesperson (fixed amount by price level). A return or a cancelled invoice adds a negative "taken back" line.
-- Paying marks lines paid and keeps one payment record (CP-…) for the commission slip.

insert into number_counter (name, prefix, digits) values ('COMMISSION_PAYMENT', 'CP-', 4);

create table commission_payment (
    id          bigint generated always as identity primary key,
    number      varchar(20)   not null unique,                 -- CP-0001
    paid_on     date          not null,
    paid_at     timestamptz   not null,
    method      varchar(12)   not null check (method in ('DRAWER', 'OWNER_CASH', 'BANK', 'SALARY')),
    amount      numeric(14,2) not null,
    payee       varchar(300)  not null,                        -- "Sunil Kumara" (or several names)
    branch_id   bigint        references branch (id),          -- null = lines from more than one branch
    paid_by_id  bigint        not null references app_user (id),
    created_at  timestamptz   not null,
    created_by  varchar(64)   not null,
    updated_at  timestamptz   not null,
    updated_by  varchar(64)   not null,
    version     bigint        not null default 0
);
create index ix_commission_payment_paid_on on commission_payment (paid_on);

create table commission_line (
    id             bigint generated always as identity primary key,
    sale_id        bigint        not null references sale (id),
    branch_id      bigint        not null references branch (id),
    earned_at      timestamptz   not null,                     -- the sale time (or when it was taken back)
    payee          varchar(5)    not null check (payee in ('GUIDE', 'STAFF')),
    guide_id       bigint        references guide (id),
    staff_id       bigint        references app_user (id),
    amount         numeric(14,2) not null,                     -- negative = taken back
    pct            numeric(5,2),                               -- guide % on the sale
    status         varchar(8)    not null check (status in ('UNPAID', 'PAID', 'REVERSED')),
    note           varchar(200),
    paid_at        timestamptz,
    method         varchar(12)   check (method in ('DRAWER', 'OWNER_CASH', 'BANK', 'SALARY')),
    payment_id     bigint        references commission_payment (id),
    created_at     timestamptz   not null,
    created_by     varchar(64)   not null,
    updated_at     timestamptz   not null,
    updated_by     varchar(64)   not null,
    version        bigint        not null default 0,
    constraint ck_commission_payee check ((payee = 'GUIDE' and guide_id is not null) or (payee = 'STAFF' and staff_id is not null))
);
create index ix_commission_line_sale on commission_line (sale_id);
create index ix_commission_line_guide on commission_line (guide_id, earned_at);
create index ix_commission_line_staff on commission_line (staff_id, earned_at);
create index ix_commission_line_open on commission_line (status) where paid_at is null;

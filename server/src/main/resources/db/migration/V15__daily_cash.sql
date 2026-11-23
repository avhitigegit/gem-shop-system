-- P3-06 Daily cash (BRD I1–I4, M4, M6; rule 11).
-- Opening cash per currency (one per branch per day), cash paid out from the drawer (small expenses, refunds; guide
-- commission paid "Cash (drawer)" comes from commission_payment), and the day-end closing (counted vs expected).

create table cash_opening (
    id            bigint generated always as identity primary key,
    branch_id     bigint        not null references branch (id),
    day           date          not null,
    lkr           numeric(14,2) not null check (lkr >= 0),
    usd           numeric(14,2) not null check (usd >= 0),
    eur           numeric(14,2) not null check (eur >= 0),
    entered_at    timestamptz   not null,
    entered_by_id bigint        not null references app_user (id),
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0,
    constraint uq_cash_opening unique (branch_id, day)
);

create table day_closing (
    id              bigint generated always as identity primary key,
    number          varchar(30)   not null unique,              -- DC-20261123 (main branch) / DC-20261123-B2
    branch_id       bigint        not null references branch (id),
    day             date          not null,
    expected_lkr    numeric(14,2) not null,
    expected_usd    numeric(14,2) not null,
    expected_eur    numeric(14,2) not null,
    counted_lkr     numeric(14,2) not null check (counted_lkr >= 0),
    counted_usd     numeric(14,2) not null check (counted_usd >= 0),
    counted_eur     numeric(14,2) not null check (counted_eur >= 0),
    card_expected   numeric(14,2) not null,
    card_counted    numeric(14,2) not null check (card_counted >= 0),
    card_fee        numeric(14,2) not null default 0,             -- bank fee on card sales (shop pays)
    sales_total     numeric(14,2) not null default 0,
    bills           integer       not null default 0,
    drawer_out      numeric(14,2) not null default 0,             -- expenses + commissions + refunds from the drawer
    rate_usd        numeric(12,4) not null,
    rate_eur        numeric(12,4) not null,
    difference      numeric(14,2) not null,                       -- in LKR at the day's rates, card included
    note            varchar(500),
    closed_at       timestamptz   not null,
    closed_by_id    bigint        not null references app_user (id),
    sent_to_owner   boolean       not null default false,
    created_at      timestamptz   not null,
    created_by      varchar(64)   not null,
    updated_at      timestamptz   not null,
    updated_by      varchar(64)   not null,
    version         bigint        not null default 0,
    constraint uq_day_closing unique (branch_id, day)
);
create index ix_day_closing_day on day_closing (day);

-- Shop expenses (M1–M7). P3-06 records the ones paid in cash from the drawer; P5-01 adds the other methods, the list and
-- monthly bills. The bill photo is a stored file (owner type EXPENSE, ref = number).
create table expense (
    id            bigint generated always as identity primary key,
    number        varchar(20)   not null unique,               -- EXP-0030
    spent_at      timestamptz   not null,
    branch_id     bigint        not null references branch (id),
    expense_type  varchar(80)   not null,
    amount        numeric(14,2) not null check (amount > 0),
    paid_to       varchar(120)  not null,
    method        varchar(12)   not null check (method in ('DRAWER', 'OWNER_CASH', 'BANK', 'CARD')),
    status        varchar(8)    not null check (status in ('APPROVED', 'PENDING', 'REJECTED')),
    approval_no   varchar(20),
    note          varchar(500),
    staff_id      bigint        references app_user (id),
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0
);
create index ix_expense_branch_spent on expense (branch_id, spent_at);

-- A refund credit note paid back in cash from the drawer.
alter table credit_note
    add column cash_refund_at        timestamptz,
    add column cash_refund_branch_id bigint references branch (id),
    add column cash_refund_by_id     bigint references app_user (id);

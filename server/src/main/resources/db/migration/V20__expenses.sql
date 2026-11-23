-- P5-01 Expenses (BRD M1–M7): monthly bills (rent, phone, salaries) added automatically on their day, and the link from
-- an expense to the bill that added it. sample_bill = the prototype's bill file name (sample data has no real photo).
create table monthly_bill (
    id            bigint generated always as identity primary key,
    code          varchar(8)    not null unique,               -- RC-01
    branch_id     bigint        not null references branch (id),
    expense_type  varchar(80)   not null,
    amount        numeric(14,2) not null check (amount > 0),
    paid_to       varchar(120)  not null,
    method        varchar(12)   not null check (method in ('OWNER_CASH', 'BANK', 'CARD')),
    day           int           not null check (day between 1 and 28),
    active        boolean       not null default true,
    last_added    date,
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0
);

alter table expense add column monthly_bill_id bigint references monthly_bill (id);
alter table expense add column sample_bill varchar(120);
create index ix_expense_monthly_bill on expense (monthly_bill_id, spent_at);
create index ix_expense_spent on expense (spent_at);

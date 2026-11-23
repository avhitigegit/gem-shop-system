-- P3-04 Returns (BRD E1, E2, D2; rules 3, 4, 7): a customer brings a gem back for a refund or an exchange. The owner
-- approves every return; then the gem is back in stock, a credit note is made and the commissions for it are taken back.

create table sale_return (
    id            bigint generated always as identity primary key,
    number        varchar(20)   not null unique,                 -- RT-0003
    requested_at  timestamptz   not null,
    branch_id     bigint        not null references branch (id),
    sale_id       bigint        not null references sale (id),
    sale_item_id  bigint        not null references sale_item (id),
    gem_id        bigint        not null references gem (id),
    kind          varchar(10)   not null check (kind in ('REFUND', 'EXCHANGE')),
    reason        varchar(500)  not null,
    amount        numeric(14,2) not null check (amount > 0),     -- the price paid for the gem
    status        varchar(10)   not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    staff_id      bigint        references app_user (id),
    approval_no   varchar(20),
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0
);
create index ix_sale_return_at on sale_return (requested_at desc);
-- One waiting return per gem line.
create unique index ux_sale_return_waiting on sale_return (sale_item_id) where status = 'PENDING';

-- Made when the owner approves a return. Never deleted (rule 4). An exchange credit note pays for the new gem.
create table credit_note (
    id            bigint generated always as identity primary key,
    number        varchar(20)   not null unique,                 -- CN-0002
    issued_at     timestamptz   not null,
    return_id     bigint        not null unique references sale_return (id),
    amount        numeric(14,2) not null check (amount > 0),
    guide_comm    numeric(14,2) not null default 0,              -- guide commission taken back
    staff_comm    numeric(14,2) not null default 0,              -- staff commission taken back
    approved_by   varchar(120)  not null,
    used_sale_no  varchar(20),                                   -- exchange: the new sale it paid for
    used_amount   numeric(14,2),
    sent_email    boolean       not null default false,
    sent_whatsapp boolean       not null default false,
    created_at    timestamptz   not null,
    created_by    varchar(64)   not null,
    updated_at    timestamptz   not null,
    updated_by    varchar(64)   not null,
    version       bigint        not null default 0
);
create index ix_credit_note_issued on credit_note (issued_at desc);

-- Exchange: the credit note is a payment on the new sale.
alter table sale_payment drop constraint sale_payment_method_check;
alter table sale_payment alter column method type varchar(6);
alter table sale_payment add constraint sale_payment_method_check check (method in ('CASH', 'CARD', 'CREDIT'));
alter table sale_payment add column credit_note_no varchar(20);

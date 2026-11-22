-- Test only: a branch-owned table with a Money column, to check the mappings against PostgreSQL.
create table if not exists test_money_record (
    id             bigint generated always as identity primary key,
    branch_id      bigint        not null references branch (id),
    amount_lkr     numeric(14,2) not null,
    currency       varchar(3)    not null,
    rate           numeric(12,4) not null,
    foreign_amount numeric(14,2) not null,
    created_at     timestamptz   not null,
    created_by     varchar(64)   not null,
    updated_at     timestamptz   not null,
    updated_by     varchar(64)   not null,
    version        bigint        not null default 0
);

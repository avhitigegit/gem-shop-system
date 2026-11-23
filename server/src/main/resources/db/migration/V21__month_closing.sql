-- P5-03 Month end (BRD N6, rule 11): a closed month is locked – nothing dated in it can be added or changed until the
-- owner re-opens it. One row per month that was ever closed; no row = open.
create table month_closing (
    id              bigint generated always as identity primary key,
    month           varchar(7)    not null unique check (month ~ '^[0-9]{4}-[0-9]{2}$'),   -- 2026-09
    status          varchar(8)    not null check (status in ('CLOSED', 'OPEN')),
    closed_at       timestamptz   not null,
    closed_by_id    bigint        references app_user (id),
    reopened_at     timestamptz,
    reopened_by_id  bigint        references app_user (id),
    reopen_reason   varchar(500),
    created_at      timestamptz   not null,
    created_by      varchar(64)   not null,
    updated_at      timestamptz   not null,
    updated_by      varchar(64)   not null,
    version         bigint        not null default 0
);

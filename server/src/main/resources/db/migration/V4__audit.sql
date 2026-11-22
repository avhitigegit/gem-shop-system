-- P1-04: activity log, owner approvals and alerts.

-- Who did what and when (K4). Rows are only ever added: the triggers below stop every change and delete.
create table activity_log (
    id         bigint generated always as identity primary key,
    at         timestamptz   not null,
    user_id    bigint        references app_user (id),  -- empty = "System (automatic)"
    user_name  varchar(120)  not null,                   -- name at that time (kept if the person is renamed later)
    action     varchar(80)   not null,
    detail     varchar(1000) not null default '',
    branch_id  bigint        references branch (id),    -- empty = whole shop (all branches)
    created_at timestamptz   not null,
    created_by varchar(64)   not null,
    updated_at timestamptz   not null,
    updated_by varchar(64)   not null,
    version    bigint        not null default 0
);
create index ix_activity_log_at on activity_log (at desc);
create index ix_activity_log_branch on activity_log (branch_id, at desc);
create index ix_activity_log_user on activity_log (user_id, at desc);

create function activity_log_read_only() returns trigger
    language plpgsql as
$$
begin
    raise exception 'The activity log cannot be edited or deleted';
end;
$$;
create trigger activity_log_no_change
    before update or delete on activity_log
    for each row execute function activity_log_read_only();
create trigger activity_log_no_truncate
    before truncate on activity_log
    for each statement execute function activity_log_read_only();

-- Requests that need the owner (K3): below-minimum price, return / exchange, invoice cancellation,
-- stock correction, large expense, change in a closed month.
create sequence approval_no_seq;
create table approval (
    id           bigint generated always as identity primary key,
    number       varchar(20)   not null unique,   -- AP-0001
    type         varchar(30)   not null check (type in ('BELOW_MIN', 'RETURN', 'CANCEL_INVOICE', 'STOCK_CORRECTION', 'EXPENSE', 'MONTH_REOPEN')),
    status       varchar(20)   not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    requested_at timestamptz   not null,
    requested_by bigint        not null references app_user (id),
    branch_id    bigint        references branch (id),   -- empty = whole shop (e.g. re-open a closed month)
    title        varchar(200)  not null,
    detail       varchar(1000) not null default '',
    amount_lkr   numeric(14,2),
    ref          varchar(40),                        -- the waiting record: held sale, return, invoice, correction, expense, month
    decided_at   timestamptz,
    decided_by   bigint        references app_user (id),
    note         varchar(500)  not null default '',
    created_at   timestamptz   not null,
    created_by   varchar(64)   not null,
    updated_at   timestamptz   not null,
    updated_by   varchar(64)   not null,
    version      bigint        not null default 0,
    constraint ck_approval_decided check ((status = 'PENDING') = (decided_at is null))
);
create index ix_approval_status on approval (status, requested_at desc);
create index ix_approval_branch on approval (branch_id);

-- Owner alerts (K2): every sale, below-minimum request, return, cancellation, stock change, cash difference …
create table alert (
    id         bigint generated always as identity primary key,
    at         timestamptz  not null,
    type       varchar(30)  not null,
    text       varchar(300) not null,
    link       varchar(300),                       -- app page to open, e.g. /sell/invoices/INV-000138
    branch_id  bigint       references branch (id),  -- empty = whole shop
    read_at    timestamptz,
    read_by    bigint       references app_user (id),
    created_at timestamptz  not null,
    created_by varchar(64)  not null,
    updated_at timestamptz  not null,
    updated_by varchar(64)  not null,
    version    bigint       not null default 0
);
create index ix_alert_at on alert (at desc);
create index ix_alert_unread on alert (branch_id) where read_at is null;

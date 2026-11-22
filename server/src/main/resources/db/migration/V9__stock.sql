-- P2-04 Stock (BRD D3–D7): transfers between branches, stock checks, stock corrections (owner approves).

create table stock_transfer (
    id             bigint generated always as identity primary key,
    number         varchar(20)  not null unique,                 -- TR-0002
    from_branch_id bigint       not null references branch (id),
    to_branch_id   bigint       not null references branch (id),
    status         varchar(12)  not null check (status in ('IN_TRANSIT', 'RECEIVED')),
    sent_at        timestamptz  not null,
    sent_by        bigint       references app_user (id),
    received_at    timestamptz,
    received_by    bigint       references app_user (id),
    notes          varchar(300),
    created_at     timestamptz  not null,
    created_by     varchar(64)  not null,
    updated_at     timestamptz  not null,
    updated_by     varchar(64)  not null,
    version        bigint       not null default 0,
    check (from_branch_id <> to_branch_id)
);
create index ix_stock_transfer_sent on stock_transfer (sent_at desc);

create table stock_transfer_item (
    id          bigint generated always as identity primary key,
    transfer_id bigint      not null references stock_transfer (id),
    line_no     int         not null,
    gem_id      bigint      not null references gem (id),
    created_at  timestamptz not null,
    created_by  varchar(64) not null,
    updated_at  timestamptz not null,
    updated_by  varchar(64) not null,
    version     bigint      not null default 0,
    unique (transfer_id, line_no),
    unique (transfer_id, gem_id)
);

create table stock_correction (
    id           bigint generated always as identity primary key,
    number       varchar(20)  not null unique,                   -- SC-0003
    gem_id       bigint       not null references gem (id),
    kind         varchar(10)  not null check (kind in ('STATUS', 'BRANCH', 'CARAT')),
    from_value   varchar(40)  not null,                          -- WRITTEN_OFF / branch code / 5.10
    to_value     varchar(40)  not null,
    reason       varchar(500) not null,
    status       varchar(10)  not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    requested_at timestamptz  not null,
    requested_by bigint       references app_user (id),
    approval_no  varchar(20),                                    -- AP-0013 (approval.ref = this number)
    created_at   timestamptz  not null,
    created_by   varchar(64)  not null,
    updated_at   timestamptz  not null,
    updated_by   varchar(64)  not null,
    version      bigint       not null default 0
);
create index ix_stock_correction_at on stock_correction (requested_at desc);
-- one waiting correction per gem
create unique index ux_stock_correction_pending on stock_correction (gem_id) where status = 'PENDING';

create table stock_check (
    id         bigint generated always as identity primary key,
    number     varchar(20)   not null unique,                    -- SCK-0002
    branch_id  bigint        not null references branch (id),
    checked_at timestamptz   not null,
    checked_by bigint        references app_user (id),
    expected   int           not null,
    found      int           not null,
    missing    varchar(4000) not null default '',                -- gem codes, comma separated
    wrong      varchar(4000) not null default '',                -- scanned but belong elsewhere / sold
    unknown    varchar(2000) not null default '',                -- scanned text that is no gem
    notes      varchar(500),
    created_at timestamptz   not null,
    created_by varchar(64)   not null,
    updated_at timestamptz   not null,
    updated_by varchar(64)   not null,
    version    bigint        not null default 0
);
create index ix_stock_check_at on stock_check (checked_at desc);

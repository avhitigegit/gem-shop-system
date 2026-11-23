-- P4-02 Phone app and push alerts (BRD K1, K2).

-- The shop's VAPID key pair, made on first use. The private key is encrypted with ENCRYPTION_KEY.
create table push_key (
    id           bigint        primary key check (id = 1),
    public_key   varchar(200)  not null,              -- base64url, uncompressed P-256 point
    secret_key   text          not null,              -- the private key: encrypted base64url PKCS#8
    created_at   timestamptz   not null default now(),
    created_by   varchar(64)   not null default 'system',
    updated_at   timestamptz   not null default now(),
    updated_by   varchar(64)   not null default 'system',
    version      bigint        not null default 0
);

-- A phone / browser that receives push alerts for a person.
create table push_device (
    id           bigint generated always as identity primary key,
    user_id      bigint        not null references app_user (id),
    endpoint     varchar(1000) not null unique,
    p256dh       varchar(200)  not null,
    auth         varchar(100)  not null,
    device       varchar(200),
    last_sent_at timestamptz,
    failures     integer       not null default 0,
    created_at   timestamptz   not null default now(),
    created_by   varchar(64)   not null default 'system',
    updated_at   timestamptz   not null default now(),
    updated_by   varchar(64)   not null default 'system',
    version      bigint        not null default 0
);
create index ix_push_device_user on push_device (user_id);

-- On / off per push kind and person. No row = on.
create table push_setting (
    id           bigint generated always as identity primary key,
    user_id      bigint        not null references app_user (id),
    kind         varchar(20)   not null check (kind in ('SALE', 'APPROVAL', 'CASH_DIFFERENCE', 'DAY_CLOSED')),
    enabled      boolean       not null,
    created_at   timestamptz   not null default now(),
    created_by   varchar(64)   not null default 'system',
    updated_at   timestamptz   not null default now(),
    updated_by   varchar(64)   not null default 'system',
    version      bigint        not null default 0,
    constraint uq_push_setting unique (user_id, kind)
);

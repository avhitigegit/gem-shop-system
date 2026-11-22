-- P1-02: login, refresh tokens, password reset, exchange rates (needed by the owner first-time setup).

alter table app_user
    add column failed_logins       integer not null default 0,
    add column locked_until        timestamptz,
    add column password_changed_at timestamptz;

-- One row per signed-in device. Only a hash of the token is stored (the browser keeps the token in a cookie).
create table refresh_token (
    id           bigint generated always as identity primary key,
    user_id      bigint       not null references app_user (id) on delete cascade,
    token_hash   varchar(64)  not null unique,
    remember     boolean      not null,
    expires_at   timestamptz  not null,
    last_used_at timestamptz  not null,
    revoked_at   timestamptz,
    replaced     boolean      not null default false,
    device       varchar(255),
    created_at   timestamptz  not null,
    created_by   varchar(64)  not null,
    updated_at   timestamptz  not null,
    updated_by   varchar(64)  not null,
    version      bigint       not null default 0
);
create index ix_refresh_token_user on refresh_token (user_id);

-- "Forgot password" links: one use, short life. Only a hash of the token is stored.
create table password_reset_token (
    id         bigint generated always as identity primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null,
    created_by varchar(64) not null,
    updated_at timestamptz not null,
    updated_by varchar(64) not null,
    version    bigint      not null default 0
);
create index ix_password_reset_user on password_reset_token (user_id);

-- Exchange rates entered by the owner (LKR for 1 USD / 1 EUR). The newest row is today's rate; older rows are the history.
create table exchange_rate (
    id           bigint generated always as identity primary key,
    currency     varchar(3)    not null check (currency in ('USD', 'EUR')),
    rate         numeric(12,4) not null check (rate > 0),
    effective_at timestamptz   not null,
    created_at   timestamptz   not null,
    created_by   varchar(64)   not null,
    updated_at   timestamptz   not null,
    updated_by   varchar(64)   not null,
    version      bigint        not null default 0
);
create index ix_exchange_rate_currency on exchange_rate (currency, effective_at desc);

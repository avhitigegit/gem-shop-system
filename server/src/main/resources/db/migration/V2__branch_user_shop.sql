-- P1-01: base tables. Every table has: id, created_at/by, updated_at/by, version (see BaseEntity).

create table branch (
    id         bigint generated always as identity primary key,
    code       varchar(20)  not null unique,
    name       varchar(120) not null,
    short_name varchar(60),
    address    varchar(255),
    phone      varchar(40),
    status     varchar(20)  not null check (status in ('ACTIVE', 'OPENING', 'INACTIVE')),
    opened_on  date,
    created_at timestamptz  not null,
    created_by varchar(64)  not null,
    updated_at timestamptz  not null,
    updated_by varchar(64)  not null,
    version    bigint       not null default 0
);

create table app_user (
    id            bigint generated always as identity primary key,
    name          varchar(120) not null,
    email         varchar(160) not null,
    phone         varchar(40),
    role          varchar(20)  not null check (role in ('OWNER', 'SALESPERSON', 'STAFF', 'ACCOUNTANT')),
    branch_id     bigint references branch (id),
    active        boolean      not null default true,
    password_hash varchar(100),
    last_login_at timestamptz,
    created_at    timestamptz  not null,
    created_by    varchar(64)  not null,
    updated_at    timestamptz  not null,
    updated_by    varchar(64)  not null,
    version       bigint       not null default 0,
    -- Salesperson and Shop Staff belong to one branch; Owner and Accountant see all branches.
    constraint ck_app_user_branch check (
        (role in ('OWNER', 'ACCOUNTANT') and branch_id is null) or
        (role in ('SALESPERSON', 'STAFF') and branch_id is not null))
);
create unique index ux_app_user_email on app_user (lower(email));
create index ix_app_user_branch on app_user (branch_id);

-- Shop details: one row. No VAT number or VAT rate (prices include VAT, VAT is never shown).
create table shop_settings (
    id                       bigint generated always as identity primary key,
    name                     varchar(120)  not null,
    tagline                  varchar(160),
    address                  varchar(255),
    phone                    varchar(40),
    email                    varchar(160),
    website                  varchar(120),
    logo_file                varchar(255),
    ngja_licence_no          varchar(60),
    ngja_licence_expiry      date,
    ngja_remind_days         integer       not null default 60 check (ngja_remind_days >= 0),
    print_licence_on_invoice boolean       not null default true,
    large_expense_limit      numeric(14,2) not null default 50000 check (large_expense_limit >= 0),
    card_fee_pct             numeric(5,2)  not null default 3 check (card_fee_pct between 0 and 100),
    invoice_prefix           varchar(10)   not null default 'INV-',
    return_policy_days       integer       not null default 14 check (return_policy_days >= 0),
    created_at               timestamptz   not null,
    created_by               varchar(64)   not null,
    updated_at               timestamptz   not null,
    updated_by               varchar(64)   not null,
    version                  bigint        not null default 0
);
create unique index ux_shop_settings_single_row on shop_settings ((true));

-- P1-06 Settings: drop-down lists and commission rules (shop, branches, staff and rates tables exist since V2/V3).

-- Drop-down lists the owner can edit (Settings → Drop-down lists). Values show in forms in this order.
create table lookup_value (
    id         bigint generated always as identity primary key,
    list_name  varchar(30)  not null check (list_name in
                   ('GEM_TYPE', 'PRODUCT_TYPE', 'COLOUR', 'SHAPE', 'TREATMENT', 'LAB', 'EXPENSE_TYPE')),
    value      varchar(80)  not null check (length(trim(value)) > 0),
    sort_order integer      not null,
    created_at timestamptz  not null,
    created_by varchar(64)  not null,
    updated_at timestamptz  not null,
    updated_by varchar(64)  not null,
    version    bigint       not null default 0
);
create unique index ux_lookup_value on lookup_value (list_name, lower(value));
create index ix_lookup_value_order on lookup_value (list_name, sort_order);

-- Commission rules (BRD A6, H1, H2): one row with the default guide / driver %, plus the staff price levels.
create table commission_rules (
    id                bigint generated always as identity primary key,
    default_guide_pct numeric(5,2) not null default 10 check (default_guide_pct between 0 and 50),
    created_at        timestamptz  not null,
    created_by        varchar(64)  not null,
    updated_at        timestamptz  not null,
    updated_by        varchar(64)  not null,
    version           bigint       not null default 0
);
create unique index ux_commission_rules_single_row on commission_rules ((true));

-- Staff commission: a fixed amount by the sale total. up_to empty = the last level ("above …").
create table commission_level (
    id         bigint generated always as identity primary key,
    level_no   integer       not null check (level_no >= 1),
    up_to      numeric(14,2) check (up_to > 0),
    amount     numeric(14,2) not null check (amount >= 0),
    created_at timestamptz   not null,
    created_by varchar(64)   not null,
    updated_at timestamptz   not null,
    updated_by varchar(64)   not null,
    version    bigint        not null default 0
);
create unique index ux_commission_level_no on commission_level (level_no);

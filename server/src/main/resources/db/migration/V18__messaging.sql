-- P4-04 Messages and birthday wishes (BRD J5, J7, J8; tech stack §6.1, §6.2).

-- The shop's message settings: one row. Passwords and tokens are encrypted with ENCRYPTION_KEY.
create table message_settings (
    id                    bigint        primary key check (id = 1),
    email_on              boolean       not null default true,       -- J7: all customer emails on / off
    smtp_host             varchar(120),
    smtp_port             integer       check (smtp_port between 1 and 65535),
    smtp_security         varchar(10)   not null default 'STARTTLS' check (smtp_security in ('STARTTLS', 'SSL', 'NONE')),
    smtp_user             varchar(160),
    smtp_password         text,                                      -- encrypted
    smtp_from             varchar(200),
    ses_enabled           boolean       not null default false,      -- offers by email (P4-05)
    ses_domain            varchar(120),
    ses_region            varchar(30),
    ses_from              varchar(200),
    ses_access_key        varchar(64),
    ses_secret            text,                                      -- encrypted
    ses_spf               boolean       not null default false,
    ses_dkim              boolean       not null default false,
    wa_enabled            boolean       not null default false,      -- WhatsApp Business (Premium), J7
    wa_phone              varchar(40),
    wa_account_id         varchar(40),
    wa_token              text,                                      -- encrypted
    wa_verified           boolean       not null default false,
    wa_verified_at        timestamptz,
    auto_invoice_email    boolean       not null default true,       -- J5 automatic messages
    auto_invoice_whatsapp boolean       not null default true,
    auto_thank_you        boolean       not null default true,
    auto_owner_closing    boolean       not null default true,
    bd_on                 boolean       not null default true,       -- J8 birthday wishes
    bd_email              boolean       not null default true,
    bd_whatsapp           boolean       not null default true,
    bd_time               time          not null default '09:00',
    bd_subject            varchar(200)  not null default 'Happy birthday from {shop}!',
    bd_text               varchar(1000) not null default 'Happy birthday, {name}! Warm wishes from all of us at {shop}.',
    created_at            timestamptz   not null default now(),
    created_by            varchar(64)   not null default 'system',
    updated_at            timestamptz   not null default now(),
    updated_by            varchar(64)   not null default 'system',
    version               bigint        not null default 0
);
insert into message_settings (id) values (1);

-- Every message the system sent (or tried to): invoices, quotations, thank-you, day-end report, birthday wishes, test email.
create table message_log (
    id           bigint generated always as identity primary key,
    at           timestamptz   not null,
    kind         varchar(20)   not null check (kind in ('INVOICE', 'QUOTATION', 'CREDIT_NOTE', 'THANK_YOU', 'CLOSING_REPORT',
                                                        'BIRTHDAY', 'TEST')),
    channel      varchar(10)   not null check (channel in ('EMAIL', 'WHATSAPP')),
    customer_id  bigint        references customer (id),
    recipient    varchar(200)  not null,
    ref          varchar(30),                                         -- INV-000141, DC-20261123, …
    subject      varchar(200),
    status       varchar(10)   not null check (status in ('SENT', 'TEST_MODE', 'FAILED')),
    automatic    boolean       not null default false,
    created_at   timestamptz   not null,
    created_by   varchar(64)   not null,
    updated_at   timestamptz   not null,
    updated_by   varchar(64)   not null,
    version      bigint        not null default 0
);
create index ix_message_log_at on message_log (at desc);
create index ix_message_log_customer on message_log (customer_id);
create index ix_message_log_ref on message_log (kind, ref);

-- One birthday wish per customer per year (J8). email / whatsapp: sent, failed, email off, no email, WhatsApp off, no number.
create table birthday_wish (
    id           bigint generated always as identity primary key,
    customer_id  bigint        not null references customer (id),
    year         integer       not null,
    at           timestamptz   not null,
    email        varchar(20)   not null,
    whatsapp     varchar(20)   not null,
    created_at   timestamptz   not null,
    created_by   varchar(64)   not null,
    updated_at   timestamptz   not null,
    updated_by   varchar(64)   not null,
    version      bigint        not null default 0,
    unique (customer_id, year)
);

-- P4-05 Offers (BRD J2, J3, J4, J6): promotions by email (Amazon SES) and WhatsApp (Meta-approved templates), only to
-- customers who gave permission and did not unsubscribe (rule 8). Numbers OF-0001 (CP- is used by commission payments).
insert into number_counter (name, prefix, digits) values ('OFFER', 'OF-', 4);

create table offer (
    id            bigint generated always as identity primary key,
    code          varchar(12)  not null unique,
    channel       varchar(10)  not null check (channel in ('EMAIL', 'WHATSAPP')),
    name          varchar(120) not null,
    subject       varchar(200),                                  -- email only
    template      varchar(30),                                   -- WhatsApp only: new_arrivals, festive_offer, thank_you
    body          text         not null,                         -- email text, or the WhatsApp "your text"
    picture       varchar(40),                                   -- gem picture chosen, "upload", or null
    photo         bytea,                                         -- the picture as PNG / JPEG (sent with the offer)
    photo_type    varchar(20),
    countries     varchar(1000) not null default '',             -- "|"-separated; empty = all
    gems          varchar(1000) not null default '',             -- gem types bought before; empty = any
    status        varchar(10)  not null default 'DRAFT' check (status in ('DRAFT', 'SENT')),
    sent_at       timestamptz,
    sent_by       bigint references app_user (id),
    sent          int          not null default 0,
    delivered     int          not null default 0,
    opened        int          not null default 0,
    clicked       int          not null default 0,
    unsubscribed  int          not null default 0,
    created_at    timestamptz  not null,
    created_by    varchar(64)  not null,
    updated_at    timestamptz  not null,
    updated_by    varchar(64)  not null,
    version       bigint       not null default 0
);
create index ix_offer_sent_at on offer (sent_at desc);

-- One row per customer an offer went to. The token is in the open pixel and the link (it says nothing about the customer)
-- and in Meta's status callbacks for WhatsApp.
create table offer_recipient (
    id              bigint generated always as identity primary key,
    offer_id        bigint       not null references offer (id),
    customer_id     bigint       not null references customer (id),
    recipient       varchar(200) not null,
    token           varchar(64)  not null unique,
    status          varchar(10)  not null check (status in ('SENT', 'TEST_MODE', 'FAILED')),
    sent_at         timestamptz  not null,
    delivered_at    timestamptz,
    opened_at       timestamptz,
    clicked_at      timestamptz,
    unsubscribed_at timestamptz,
    created_at      timestamptz  not null default now(),
    created_by      varchar(64)  not null,
    updated_at      timestamptz  not null default now(),
    updated_by      varchar(64)  not null,
    version         bigint       not null default 0,
    unique (offer_id, customer_id)
);
create index ix_offer_recipient_customer on offer_recipient (customer_id, sent_at desc);

alter table message_log drop constraint message_log_kind_check;
alter table message_log add constraint message_log_kind_check
    check (kind in ('INVOICE', 'QUOTATION', 'CREDIT_NOTE', 'THANK_YOU', 'CLOSING_REPORT', 'BIRTHDAY', 'TEST', 'OFFER'));

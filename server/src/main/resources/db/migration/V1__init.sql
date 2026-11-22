-- P0-02: first migration. The real tables start in P1-01.
create table app_meta (
    meta_key   varchar(64) primary key,
    meta_value text        not null,
    updated_at timestamptz not null default now()
);

insert into app_meta (meta_key, meta_value) values ('schema_started', 'P0-02 Project skeleton');

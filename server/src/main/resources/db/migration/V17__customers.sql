-- P4-03 Customers (BRD J1, J2, J8): when and how a customer unsubscribed, and the private token of the unsubscribe link
-- in offers (https://…/unsubscribe/<token>). The token is random and says nothing about the customer.
alter table customer add column unsubscribed_at timestamptz;
alter table customer add column unsub_via varchar(10) check (unsub_via in ('LINK', 'WHATSAPP'));
alter table customer add column unsub_token varchar(64);
update customer set unsub_token = replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
alter table customer alter column unsub_token set not null;
alter table customer add constraint ux_customer_unsub_token unique (unsub_token);

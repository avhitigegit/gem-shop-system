-- P3-02 Payment and invoices: an invoice is never deleted; cancelling needs the owner's approval (BRD G6, rule 4 / 7).
alter table sale
    add column cancel_approval_no varchar(20),           -- the latest cancellation request (AP-…)
    add column cancel_reason      varchar(500),
    add column cancelled_at       timestamptz,
    add column cancelled_by       varchar(120);

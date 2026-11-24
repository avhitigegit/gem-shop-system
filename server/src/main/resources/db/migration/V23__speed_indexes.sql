-- P6-01 speed: indexes for the lists every screen filters by branch and date, and for the links (foreign keys) the
-- app follows from a sale, return, quote or transfer. Found by listing foreign keys without an index on the full sample data.

-- sales, returns, quotes and held sales of one branch, newest first (Sales home, reports, close the day)
create index sale_branch_sold_idx              on sale (branch_id, sold_at desc);
create index sale_staff_sold_idx               on sale (staff_id, sold_at desc);
create index sale_guide_idx                    on sale (guide_id);
create index sale_return_sale_idx              on sale_return (sale_id);
create index sale_return_gem_idx               on sale_return (gem_id);
create index sale_return_branch_idx            on sale_return (branch_id, requested_at desc);
create index quote_branch_created_idx          on quote (branch_id, created_on desc);
create index quote_customer_idx                on quote (customer_id);
create index quote_item_gem_idx                on quote_item (gem_id);
create index held_sale_branch_idx              on held_sale (branch_id);
create index held_sale_item_gem_idx            on held_sale_item (gem_id);

-- commission and money
create index commission_line_branch_earned_idx on commission_line (branch_id, earned_at);
create index commission_line_payment_idx       on commission_line (payment_id);
create index commission_payment_branch_idx     on commission_payment (branch_id, paid_on);
create index credit_note_refund_branch_idx     on credit_note (cash_refund_branch_id);
create index expense_staff_idx                 on expense (staff_id);
create index monthly_bill_branch_idx           on monthly_bill (branch_id);

-- stock
create index purchase_branch_date_idx          on purchase (branch_id, purchase_date desc);
create index stock_transfer_from_idx           on stock_transfer (from_branch_id, sent_at desc);
create index stock_transfer_to_idx             on stock_transfer (to_branch_id, sent_at desc);
create index stock_transfer_item_gem_idx       on stock_transfer_item (gem_id);
create index stock_check_branch_idx            on stock_check (branch_id, checked_at desc);
create index gem_history_user_idx              on gem_history (user_id);
create index stored_file_branch_idx            on stored_file (branch_id);

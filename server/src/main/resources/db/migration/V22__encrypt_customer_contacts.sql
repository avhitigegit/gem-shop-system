-- P6-01 security check: customer email and WhatsApp number are stored encrypted like the passport
-- (Security & Data Agreement: "protected customer details (PDPA)"). Encrypted values are longer, so the columns become text.
-- Rows already in the database are encrypted right after this migration by customers.EncryptCustomerContacts
-- (it needs the app's ENCRYPTION_KEY, which SQL does not have).
alter table customer alter column email type text;
alter table customer alter column whatsapp type text;

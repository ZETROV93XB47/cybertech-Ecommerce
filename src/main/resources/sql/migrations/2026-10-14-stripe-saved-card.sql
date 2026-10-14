-- Saved card at Stripe (prompt 10 of the front's lot 2), MySQL 8.
--
-- Cards are now entered in Stripe Elements: the backend stores the Stripe PaymentMethod id, never
-- a card number. Cards saved before cannot be moved to Stripe (that would mean sending their
-- numbers to the Stripe API, which a non-PCI backend must not do), so they are purged: their owners
-- add their card again. Until this script runs, the application treats them as "no card".

-- 1. Production only (ddl-auto=validate). Dev databases (ddl-auto=update) already got these two
--    columns at startup: skip this step there (MySQL has no ADD COLUMN IF NOT EXISTS).
ALTER TABLE userTable ADD COLUMN stripeCustomerId VARCHAR(255) NULL UNIQUE;
ALTER TABLE bankCardTable ADD COLUMN stripePaymentMethodId VARCHAR(255) NULL UNIQUE;

-- 2. Everywhere: purge the cards that held an encrypted number, then drop that column.
DELETE FROM bankCardTable WHERE stripePaymentMethodId IS NULL;
ALTER TABLE bankCardTable DROP COLUMN encryptedNumber;

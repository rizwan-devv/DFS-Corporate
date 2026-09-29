-- Mock virtual cards show a full number and CVV. Still not sent to CMS.

ALTER TABLE virtual_card_orders
    ADD COLUMN pan VARCHAR(19) NULL AFTER masked_pan,
    ADD COLUMN cvv VARCHAR(3) NULL AFTER pan;

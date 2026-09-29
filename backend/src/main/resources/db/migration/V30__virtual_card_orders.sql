-- Portal mock virtual cards. Ordered by the merchant, approved in back office.
-- Physical cards stay in CMS and are not stored here.

CREATE TABLE virtual_card_orders (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id         VARCHAR(36)  NOT NULL UNIQUE,
    party_id          BIGINT       NOT NULL,
    relationship_num  VARCHAR(32)  NOT NULL,
    emboss_name       VARCHAR(40)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    masked_pan        VARCHAR(32)  NULL,
    last4             VARCHAR(4)   NULL,
    expiry            VARCHAR(8)   NULL,
    requested_by      VARCHAR(200) NULL,
    decided_by        VARCHAR(200) NULL,
    decision_note     VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at        TIMESTAMP    NULL,
    CONSTRAINT fk_virtual_card_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

CREATE INDEX idx_virtual_card_party ON virtual_card_orders(party_id);
CREATE INDEX idx_virtual_card_status ON virtual_card_orders(status);

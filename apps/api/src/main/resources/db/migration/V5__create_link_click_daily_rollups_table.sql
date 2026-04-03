CREATE TABLE link_click_daily_rollups (
    slug VARCHAR(64) NOT NULL,
    rollup_date DATE NOT NULL,
    click_count BIGINT NOT NULL,
    PRIMARY KEY (slug, rollup_date),
    CONSTRAINT fk_link_click_daily_rollups_slug FOREIGN KEY (slug) REFERENCES links (slug) ON DELETE CASCADE
);

CREATE INDEX idx_link_click_daily_rollups_date ON link_click_daily_rollups (rollup_date);

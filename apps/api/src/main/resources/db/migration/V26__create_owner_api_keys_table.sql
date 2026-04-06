ALTER TABLE owner_api_keys
    ADD COLUMN IF NOT EXISTS key_prefix VARCHAR(32);

ALTER TABLE owner_api_keys
    ADD COLUMN IF NOT EXISTS label VARCHAR(100);

ALTER TABLE owner_api_keys
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE owner_api_keys
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE owner_api_keys
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE owner_api_keys
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

ALTER TABLE owner_api_keys
    ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(100);

UPDATE owner_api_keys
SET label = COALESCE(label, key_label);

UPDATE owner_api_keys
SET key_prefix = CASE key_hash
    WHEN '5cd81fa8d1b30a1619001c1fd727555a9a17cc23d551587bb214dccbd1f59606' THEN 'free-owner-a'
    WHEN 'cf902ffdf26b6dc7450ee9d173b5766fbadcafbc19a8c7b1d320c831ecb886e7' THEN 'pro-owner-ap'
    ELSE SUBSTRING(key_hash, 1, 12)
END
WHERE key_prefix IS NULL;

UPDATE owner_api_keys
SET created_by = COALESCE(created_by, 'bootstrap')
WHERE created_by IS NULL;

ALTER TABLE owner_api_keys
    ALTER COLUMN key_prefix SET NOT NULL;

ALTER TABLE owner_api_keys
    ALTER COLUMN label SET NOT NULL;

ALTER TABLE owner_api_keys
    ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE owner_api_keys
    ADD CONSTRAINT uk_owner_api_keys_key_prefix UNIQUE (key_prefix);

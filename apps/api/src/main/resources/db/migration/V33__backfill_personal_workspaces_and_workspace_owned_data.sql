INSERT INTO workspaces (slug, display_name, personal_workspace, created_at, created_by_owner_id, archived_at)
SELECT
    CASE
        WHEN owner_slug IS NOT NULL
            THEN owner_slug
        ELSE 'owner-' || CAST(id AS VARCHAR)
    END,
    display_name || ' Personal',
    TRUE,
    created_at,
    id,
    NULL
FROM (
    SELECT
        o.id,
        o.display_name,
        o.created_at,
        CASE
            WHEN normalized_slug IS NOT NULL
                 AND LENGTH(normalized_slug) BETWEEN 3 AND 60
                THEN normalized_slug
            ELSE NULL
        END AS owner_slug
    FROM (
        SELECT
            owners.*,
            TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(owner_key), '[^a-z0-9-]', '-')) AS normalized_slug
        FROM owners
    ) o
) candidates
WHERE NOT EXISTS (
    SELECT 1
    FROM workspaces w
    WHERE w.personal_workspace = TRUE
      AND w.created_by_owner_id = candidates.id
);

INSERT INTO workspace_members (workspace_id, owner_id, role, joined_at, added_by_owner_id, removed_at)
SELECT w.id, o.id, 'OWNER', w.created_at, NULL, NULL
FROM owners o
JOIN workspaces w
  ON w.created_by_owner_id = o.id
 AND w.personal_workspace = TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM workspace_members wm
    WHERE wm.workspace_id = w.id
      AND wm.owner_id = o.id
);

UPDATE links l
SET workspace_id = w.id
FROM workspaces w
WHERE l.workspace_id IS NULL
  AND w.personal_workspace = TRUE
  AND w.created_by_owner_id = l.owner_id;

UPDATE owner_api_keys k
SET workspace_id = w.id
FROM workspaces w
WHERE k.workspace_id IS NULL
  AND w.personal_workspace = TRUE
  AND w.created_by_owner_id = k.owner_id;

UPDATE owner_security_events e
SET workspace_id = w.id
FROM workspaces w
WHERE e.workspace_id IS NULL
  AND e.owner_id IS NOT NULL
  AND w.personal_workspace = TRUE
  AND w.created_by_owner_id = e.owner_id;

UPDATE link_catalog_projection p
SET workspace_id = w.id
FROM workspaces w
WHERE p.workspace_id IS NULL
  AND w.personal_workspace = TRUE
  AND w.created_by_owner_id = p.owner_id;

UPDATE link_discovery_projection p
SET workspace_id = w.id
FROM workspaces w
WHERE p.workspace_id IS NULL
  AND w.personal_workspace = TRUE
  AND w.created_by_owner_id = p.owner_id;

UPDATE link_activity_events e
SET workspace_id = w.id
FROM workspaces w
WHERE e.workspace_id IS NULL
  AND w.personal_workspace = TRUE
  AND w.created_by_owner_id = e.owner_id;

UPDATE projection_jobs j
SET workspace_id = w.id
FROM workspaces w
WHERE j.workspace_id IS NULL
  AND j.owner_id IS NOT NULL
  AND w.personal_workspace = TRUE
  AND w.created_by_owner_id = j.owner_id;

ALTER TABLE links
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE owner_api_keys
    ALTER COLUMN workspace_id SET NOT NULL;

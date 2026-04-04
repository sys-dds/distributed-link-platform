package com.linkplatform.api.owner.application;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOwnerStore implements OwnerStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOwnerStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuthenticatedOwner> findByApiKeyHash(String apiKeyHash) {
        return jdbcTemplate.query(
                        """
                        SELECT o.id, o.owner_key, o.plan
                        FROM owner_api_keys k
                        JOIN owners o ON o.id = k.owner_id
                        WHERE k.key_hash = ?
                        """,
                        (resultSet, rowNum) -> new AuthenticatedOwner(
                                resultSet.getLong("id"),
                                resultSet.getString("owner_key"),
                                OwnerPlan.valueOf(resultSet.getString("plan"))),
                        apiKeyHash)
                .stream()
                .findFirst();
    }

    @Override
    public void lockById(long ownerId) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM owners WHERE id = ? FOR UPDATE",
                Long.class,
                ownerId);
    }
}

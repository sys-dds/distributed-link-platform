package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcLinkMutationIdempotencyStore implements LinkMutationIdempotencyStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcLinkMutationIdempotencyStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LinkMutationIdempotencyRecord> findByKey(String idempotencyKey) {
        return jdbcTemplate.query(
                        """
                        SELECT idempotency_key, operation, request_hash, response_json, created_at
                        FROM link_mutation_idempotency
                        WHERE idempotency_key = ?
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    @Override
    public void saveResult(
            String idempotencyKey,
            String operation,
            String requestHash,
            LinkMutationResult result,
            OffsetDateTime createdAt) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_mutation_idempotency (
                        idempotency_key, operation, request_hash, response_json, created_at
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    idempotencyKey,
                    operation,
                    requestHash,
                    serialize(result),
                    createdAt);
        } catch (DuplicateKeyException exception) {
            throw new LinkMutationConflictException("Idempotency key already recorded for a different mutation state");
        }
    }

    private LinkMutationIdempotencyRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new LinkMutationIdempotencyRecord(
                resultSet.getString("idempotency_key"),
                resultSet.getString("operation"),
                resultSet.getString("request_hash"),
                deserialize(resultSet.getString("response_json")),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private String serialize(LinkMutationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Mutation idempotency result could not be serialized", exception);
        }
    }

    private LinkMutationResult deserialize(String json) {
        try {
            return objectMapper.readValue(json, LinkMutationResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Mutation idempotency result could not be deserialized", exception);
        }
    }
}

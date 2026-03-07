--liquibase formatted sql

--changeset wkrzywiec:7_1
--comment: Add vector extension
CREATE EXTENSION vector;


--changeset wkrzywiec:7_2
--comment: Create table for recipe_embeddings
CREATE TABLE recipe_embeddings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id               UUID NOT NULL,
    chunk_type              VARCHAR(255),
    token_count             INTEGER,
    embedding               VECTOR(1536)
);
--liquibase formatted sql

--changeset wkrzywiec:3_1
--comment: Create table for recipe-ingredient relationships
CREATE TABLE IF NOT EXISTS recipe_ingredient (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id uuid NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    section VARCHAR,
    original_text VARCHAR
);

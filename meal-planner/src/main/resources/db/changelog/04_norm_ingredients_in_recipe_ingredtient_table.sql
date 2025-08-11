--liquibase formatted sql

--changeset wkrzywiec:4_1
--comment: Add columns for normalized ingredients in recipe_ingredient table
ALTER TABLE recipe_ingredient
    ADD COLUMN amount VARCHAR(255),
    ADD COLUMN amount_unit VARCHAR(255),
    ADD COLUMN ingredient_name VARCHAR(255),
    ADD COLUMN ingredient_name_eng VARCHAR(255);


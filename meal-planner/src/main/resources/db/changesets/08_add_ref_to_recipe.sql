--liquibase formatted sql

--changeset wkrzywiec:8_1
--comment: Add recipe reference to the recipe_embeddings
ALTER TABLE recipe_embeddings
    ADD CONSTRAINT fk_recipe_embeddings_recipe
    FOREIGN KEY (recipe_id)
    REFERENCES recipe(id);

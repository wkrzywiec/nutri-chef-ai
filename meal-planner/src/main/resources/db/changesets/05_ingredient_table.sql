--liquibase formatted sql

--changeset wkrzywiec:5_1
--comment: Crerate table for ingredients
CREATE TABLE ingredient (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255) NOT NULL,
    name_eng                VARCHAR(255),
    amount_unit             VARCHAR(255),
    amount_unit_eng         VARCHAR(255),
    calories_per_unit       NUMERIC(10, 2)
);


--changeset wkrzywiec:5_2
--comment: Insert distinct ingredients from recipe_ingredient table
INSERT INTO ingredient (name, name_eng, amount_unit)
SELECT DISTINCT ingredient_name, ingredient_name_eng, amount_unit
FROM recipe_ingredient
WHERE ingredient_name IS NOT NULL;

--changeset wkrzywiec:5_3
--comment: Add ingredient_id column to recipe_ingredient table
ALTER TABLE recipe_ingredient ADD COLUMN ingredient_id UUID;

--changeset wkrzywiec:5_4
--comment: Update recipe_ingredient with ingredient_id from ingredient table
UPDATE recipe_ingredient ri
SET ingredient_id = i.id
FROM ingredient i
WHERE ri.ingredient_name = i.name
  AND ri.ingredient_name_eng = i.name_eng
  AND ri.amount_unit = i.amount_unit;


--changeset wkrzywiec:5_5
--comment: Add foreign key constraint to recipe_ingredient table
ALTER TABLE recipe_ingredient
ADD CONSTRAINT fk_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id);

--changeset wkrzywiec:5_6
--comment: Remove old columns from recipe_ingredient table
ALTER TABLE recipe_ingredient
DROP COLUMN amount_unit,
DROP COLUMN ingredient_name,
DROP COLUMN ingredient_name_eng;


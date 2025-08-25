--liquibase formatted sql

--changeset wkrzywiec:6_1
--comment: Add nutritional information columns to ingredient table
ALTER TABLE ingredient 
ADD COLUMN protein_per_unit NUMERIC(10, 4),
ADD COLUMN fat_per_unit NUMERIC(10, 4),
ADD COLUMN carbohydrates_per_unit NUMERIC(10, 4),
ADD COLUMN fiber_per_unit NUMERIC(10, 4);


--changeset wkrzywiec:6_2
--comment: Update ingredient table with nutritional information
ALTER TABLE ingredient 
ALTER COLUMN calories_per_unit TYPE NUMERIC(10, 4);
--liquibase formatted sql

--changeset wkrzywiec:2_1
ALTER TABLE recipe 
ADD COLUMN tags JSONB;


--changeset wkrzywiec:2_2
ALTER TABLE recipe_scraper
ADD COLUMN error TEXT,
ADD COLUMN executed_at TIMESTAMPTZ DEFAULT NOW();



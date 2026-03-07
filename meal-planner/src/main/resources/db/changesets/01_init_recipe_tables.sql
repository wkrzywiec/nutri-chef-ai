--liquibase formatted sql

--changeset wkrzywiec:1_1
CREATE TABLE recipe (
    id                      UUID PRIMARY KEY,
    name                    VARCHAR NOT NULL,
    description             TEXT,
    source                  VARCHAR NOT NULL,
    source_url              VARCHAR,
    servings                VARCHAR,
    ingredients             JSONB,
    instructions            JSONB,
    advices                 JSONB,
    img_url                 VARCHAR,
    estimated_time_minutes  JSONB,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

--changeset wkrzywiec:1_2
CREATE TABLE recipe_scraper (
    url         VARCHAR,
    done        BOOL NOT NULL DEFAULT false,
    recipe_id   UUID REFERENCES recipe(id)
);


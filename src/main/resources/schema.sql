-- =============================================================================
-- PlantDoctor - schema
--
-- Applied automatically only in the "mock" profile (in-memory H2 database).
-- The prod profile sets spring.sql.init.mode=never, because a real deployment
-- already holds these tables.
--
-- Table 1: caas_diseases_pests_agridata
--   Knowledge records, one row per pest / disease / weed. Column names are kept
--   identical to the production knowledge platform so that the JSON contract
--   returned by /api/knowledge does not change between deployments.
--
-- Table 2: plantdoctor_access_log
--   One row per diagnosis request, including failures and requests whose label
--   found no knowledge record. Required for reporting the proportion of
--   successful, failed and unmatched diagnoses.
-- =============================================================================

DROP TABLE IF EXISTS caas_diseases_pests_agridata;

CREATE TABLE caas_diseases_pests_agridata (
    id                          INT AUTO_INCREMENT PRIMARY KEY,
    guid                        VARCHAR(64),
    recommend                   VARCHAR(16),
    number                      VARCHAR(32),
    zh_name                     VARCHAR(128) NOT NULL,
    en_name                     VARCHAR(256),
    zh_alias_name               VARCHAR(512),
    introduction                TEXT,
    canonical_main_harm_crops   VARCHAR(512),
    main_harm_crops             VARCHAR(512),
    hazard_symptoms             TEXT,
    pathogens_characteristics   TEXT,
    driver                      TEXT,
    characteristics             TEXT,
    prevention_methods          TEXT,
    page_url                    VARCHAR(512),
    source_database             VARCHAR(128),
    image_name                  VARCHAR(256),
    image_url                   VARCHAR(512),
    -- 0: disease, 1: insect pest, 2: weed
    type                        VARCHAR(8),
    senseagro_img_path          VARCHAR(512),
    senseagro_name              VARCHAR(128),
    created_datetime            TIMESTAMP,
    last_updated_datetime       TIMESTAMP
);

CREATE INDEX idx_agridata_zh_name ON caas_diseases_pests_agridata (zh_name);


DROP TABLE IF EXISTS plantdoctor_access_log;

CREATE TABLE plantdoctor_access_log (
    log_id            VARCHAR(64) PRIMARY KEY,
    created_at        TIMESTAMP,
    crop_type         VARCHAR(16),
    image_name        VARCHAR(256),
    provider          VARCHAR(32),
    insect_name       VARCHAR(128),
    trust_level       DOUBLE,
    knowledge_matched BOOLEAN,
    request_param     VARCHAR(2000),
    response_text     VARCHAR(4000)
);

CREATE INDEX idx_access_log_created_at ON plantdoctor_access_log (created_at);

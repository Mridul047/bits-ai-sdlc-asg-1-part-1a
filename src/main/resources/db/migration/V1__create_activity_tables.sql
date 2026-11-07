CREATE TABLE activity_summaries (
    activity_id  BIGINT PRIMARY KEY,
    start_date   TIMESTAMPTZ NOT NULL,
    summary_json JSONB NOT NULL
);

CREATE INDEX idx_summaries_start_date
    ON activity_summaries (start_date DESC);

CREATE TABLE activity_details (
    activity_id BIGINT PRIMARY KEY,
    start_date  TIMESTAMPTZ NOT NULL,
    detail_json JSONB NOT NULL
);

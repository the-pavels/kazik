CREATE TABLE users
(
    id    UUID NOT NULL PRIMARY KEY,
    state JSONB NOT NULL
);

CREATE TABLE tables
(
    id    UUID NOT NULL PRIMARY KEY,
    state JSONB NOT NULL
);

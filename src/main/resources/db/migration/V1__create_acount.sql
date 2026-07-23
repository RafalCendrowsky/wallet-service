create table account (
    id          uuid primary key default uuidv7(),
    owner       varchar(255) not null,
    created_at  timestamp not null default now()
);

create table customer (
    id uuid primary key default uuidv7(),
    email varchar(255) not null unique,
    created_at timestamp not null default now()
);

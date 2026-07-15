create table transfer (
    id              uuid primary key default uuidv7(),
    from_account    uuid not null references account(id),
    to_account      uuid not null references account(id),
    amount          decimal(15,2) not null,
    idempotency_key varchar(255) not null,
    created_at      timestamp default now()
);

create index transfer_idempotency_key_idx on transfer(idempotency_key);

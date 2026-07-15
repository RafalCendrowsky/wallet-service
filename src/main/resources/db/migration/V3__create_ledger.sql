create table ledger (
    id              uuid primary key default uuidv7(),
    account_id      uuid not null references account(id),
    transfer_id     uuid not null references transfer(id),
    amount          decimal(15,2) not null,
    type            varchar(255) not null,
    created_at      timestamp default now()
);

create table account_balance (
    account_id      uuid primary key references account(id),
    balance         decimal(15,2) not null,
    updated_at      timestamp not null default now()
);

create table hold (
    id          uuid primary key default uuidv7(),
    account_id  uuid not null references account(id),
    amount      decimal(15,2) not null,
    status      varchar(20) not null default 'ACTIVE',
    expires_at  timestamp not null,
    created_at  timestamp not null default now()
);

create index hold_account_status_idx on hold(account_id, status);

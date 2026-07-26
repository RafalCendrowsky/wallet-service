alter table account_balance add column last_entry_id uuid references ledger(id);

update ledger set amount = -amount where type = 'CREDIT';
alter table ledger drop column type;
alter table ledger rename to ledger_entry;

create table account_balance_queue(
    account_id uuid primary key references account(id),
    created_at timestamp not null default now()
);

alter table hold
    rename column wallet_id to from_wallet;
alter table hold
    add column to_wallet uuid not null references wallet(id);

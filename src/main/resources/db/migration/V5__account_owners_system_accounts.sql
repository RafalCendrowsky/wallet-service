alter table account drop column owner;
alter table account
    add column owner_id uuid references customer(id),
    add column status varchar(255) not null default 'ACTIVE',
    add column type varchar(255) not null default 'SYSTEM';

create table system_account (
    account_id uuid primary key references account(id),
    role varchar(255) not null
)

alter table account drop column owner;
alter table account
    add column status varchar(255) not null default 'ACTIVE',
    add column type varchar(255) not null default 'SERVICE';

create table customer_account (
    account_id  uuid primary key references account(id),
    customer_id uuid references customer(id)
);

create table service_account (
    account_id  uuid primary key references account(id),
    role        varchar(255) not null,
    constraint uq_service_account_role unique (role)
);

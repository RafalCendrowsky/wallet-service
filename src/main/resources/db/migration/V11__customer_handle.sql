alter table customer add column handle varchar(255) not null unique;
alter table customer_wallet add constraint uq_customer_wallet_customer_id unique (customer_id);

create table customer_identity (
    customer_id  uuid not null references customer(id),
    issuer       varchar(512) not null,
    external_id  varchar(255) not null,
    email        varchar(255),
    created_at   timestamp not null default now(),
    primary key (customer_id, issuer, external_id)
);

create unique index idx_customer_identity_issuer_external_id on customer_identity(issuer, external_id);

alter table customer drop column email;
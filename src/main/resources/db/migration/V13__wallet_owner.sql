create table service_account(
    id              uuid primary key default uuidv7(),
    role            varchar not null unique,
    display_name    varchar not null
);

alter table customer add column display_name varchar not null;
alter table wallet drop column type;

drop table customer_wallet;
drop table service_wallet;

create table wallet_owner(
    wallet_id   uuid primary key references wallet(id),
    customer_id uuid references customer(id),
    service_id  uuid references service_account(id)
);

create index wallet_owner_customer_id_idx on wallet_owner(customer_id);
create index wallet_owner_service_id_idx on wallet_owner(service_id);

create view wallet_owner_view as
select
    w.id                                        as wallet_id,
    coalesce(c.id, sa.id)                       as owner_id,
    coalesce(c.display_name, sa.display_name)   as owner_display_name,
    coalesce(c.handle, sa.role)                 as owner_label,
    wo.customer_id,
    wo.service_id,
    case when c.id is not null
        then 'CUSTOMER'
        else 'SERVICE'
    end as owner_type
from wallet w
    left join wallet_owner wo on w.id = wo.wallet_id
    left join customer c on wo.customer_id = c.id
    left join service_account sa on wo.service_id = sa.id;

alter table account rename to wallet;
alter table account_balance rename to wallet_balance;
alter table account_balance_queue rename to wallet_balance_queue;

alter table ledger_entry rename column account_id to wallet_id;
alter table wallet_balance rename column account_id to wallet_id;
alter table wallet_balance_queue rename column account_id to wallet_id;

alter table customer_account rename to customer_wallet;
alter table service_account rename to service_wallet;

alter table customer_wallet rename column account_id to wallet_id;
alter table service_wallet rename column account_id to wallet_id;

alter table hold rename column account_id to wallet_id;

alter table transfer rename column from_account to from_wallet;
alter table transfer rename column to_account to to_wallet;

alter table customer_wallet rename constraint customer_account_pkey to customer_wallet_pkey;
alter table customer_wallet rename constraint customer_account_account_id_fkey to customer_wallet_wallet_id_fkey;
alter table customer_wallet rename constraint customer_account_customer_id_fkey to customer_wallet_customer_id_fkey;

alter table service_wallet rename constraint service_account_pkey to service_wallet_pkey;
alter table service_wallet rename constraint uq_service_account_role to uq_service_wallet_role;
alter table service_wallet rename constraint service_account_account_id_fkey to service_wallet_wallet_id_fkey;

alter table wallet rename constraint account_pkey to wallet_pkey;

alter table wallet_balance rename constraint account_balance_pkey to wallet_balance_pkey;
alter table wallet_balance rename constraint account_balance_account_id_fkey to wallet_balance_wallet_id_fkey;
alter table wallet_balance rename constraint account_balance_last_entry_id_fkey to wallet_balance_last_entry_id_fkey;

alter table wallet_balance_queue rename constraint account_balance_queue_wallet_id_fkey to wallet_balance_queue_wallet_id_fkey;
alter table wallet_balance_queue rename constraint account_balance_queue_pkey to wallet_balance_queue_pkey;

alter table hold rename constraint hold_account_id_fkey to hold_wallet_id_fkey;

alter table ledger_entry rename constraint ledger_account_id_fkey to ledger_entry_wallet_id_fkey;
alter table ledger_entry rename constraint ledger_transfer_id_fkey to ledger_entry_transfer_id_fkey;

alter table transfer rename constraint transfer_from_account_fkey to transfer_from_wallet_fkey;
alter table transfer rename constraint transfer_to_account_fkey to transfer_to_wallet_fkey;

alter index hold_account_status_idx rename to hold_wallet_status_idx;
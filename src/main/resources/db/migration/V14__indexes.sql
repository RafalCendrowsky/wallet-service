create index ledger_entry_wallet_id_idx on ledger_entry (wallet_id);

create index hold_from_wallet_idx on hold (from_wallet);

create index transfer_from_wallet_idx on transfer (from_wallet);
create index transfer_to_wallet_idx on transfer (to_wallet);

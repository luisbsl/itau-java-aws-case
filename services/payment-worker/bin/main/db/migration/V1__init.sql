create table if not exists payment_events (
  id bigserial primary key,
  payment_id varchar(64) not null,
  amount numeric(18,2) not null,
  customer_id varchar(64) not null,
  received_at timestamptz default now()
);

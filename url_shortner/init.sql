create table if not exists urls (
   id           bigserial primary key,
   short_code   varchar(10) not null unique,
   original_url text not null,
   user_id      varchar(50),
   click_count  bigint default 0,
   created_at   timestamp default now(),
   expires_at   timestamp
);

create index if not exists idx_short_code on
   urls (
      short_code
   );
create index if not exists idx_user_id on
   urls (
      user_id
   );

create table if not exists click_analytics (
   id           bigserial primary key,
   short_code   varchar(20) not null,
   original_url text not null,
   user_id      varchar(100),
   clicked_at   timestamp not null
);

create index if not exists idx_urls_original_url on
   urls (
      original_url
   );
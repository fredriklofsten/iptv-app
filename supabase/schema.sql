-- IPTV-spelare: konton, spellistor, favoriter och TV-inloggning med kod

create table public.playlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null,
  url text,
  content text,
  created_at timestamptz not null default now()
);
create index playlists_user_idx on public.playlists(user_id);

create table public.favorites (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  channel_url text not null,
  name text,
  logo text,
  group_title text,
  created_at timestamptz not null default now(),
  primary key (user_id, channel_url)
);

create table public.user_settings (
  user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  last_playlist_id uuid references public.playlists(id) on delete set null,
  last_channel_url text,
  last_channel_name text,
  updated_at timestamptz not null default now()
);
create index user_settings_playlist_idx on public.user_settings(last_playlist_id);

alter table public.playlists enable row level security;
alter table public.favorites enable row level security;
alter table public.user_settings enable row level security;

create policy "egna spellistor" on public.playlists for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "egna favoriter" on public.favorites for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "egna installningar" on public.user_settings for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- TV-koder: ingen direkt åtkomst, bara via funktionerna nedan och edge-funktionen
create table public.tv_codes (
  code text primary key,
  secret_hash text not null,
  token_hash text,
  approved_by uuid references auth.users(id) on delete cascade,
  expires_at timestamptz not null default now() + interval '10 minutes'
);
create index tv_codes_approved_idx on public.tv_codes(approved_by);
alter table public.tv_codes enable row level security;

-- TV:n skapar en kod (anonymt). Hemligheten stannar på TV:n, bara dess hash sparas.
create or replace function public.create_tv_code(p_secret text)
returns text language plpgsql security definer set search_path = '' as $$
declare v_code text; chars text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
  if length(p_secret) < 20 then raise exception 'ogiltig hemlighet'; end if;
  delete from public.tv_codes where expires_at < now();
  loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(chars, 1 + floor(random() * length(chars))::int, 1);
    end loop;
    exit when not exists (select 1 from public.tv_codes where code = v_code);
  end loop;
  insert into public.tv_codes(code, secret_hash)
    values (v_code, encode(extensions.digest(p_secret, 'sha256'), 'hex'));
  return v_code;
end $$;

-- TV:n frågar om koden är godkänd. Svarar med engångstoken och tar bort koden.
create or replace function public.poll_tv_code(p_code text, p_secret text)
returns text language plpgsql security definer set search_path = '' as $$
declare v_token text;
begin
  delete from public.tv_codes
   where code = p_code
     and secret_hash = encode(extensions.digest(p_secret, 'sha256'), 'hex')
     and token_hash is not null
     and expires_at > now()
  returning token_hash into v_token;
  return v_token;
end $$;

revoke all on function public.create_tv_code(text) from public;
revoke all on function public.poll_tv_code(text, text) from public;
grant execute on function public.create_tv_code(text) to anon, authenticated;
grant execute on function public.poll_tv_code(text, text) to anon, authenticated;

-- Realtid: TV:n uppdateras direkt när mobilen ändrar favoriter eller spellistor
alter publication supabase_realtime add table public.favorites, public.playlists;

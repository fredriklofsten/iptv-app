// Godkänner en TV-kod: den inloggade mobilanvändaren kopplar TV:n till sitt konto.
// TV:n får en engångstoken som ger den en egen, separat inloggning.
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Fel metod" }, 405);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const jwt = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
  const { data: { user } } = await admin.auth.getUser(jwt);
  if (!user?.email) return json({ error: "Du måste vara inloggad" }, 401);

  let code = "";
  try { code = String((await req.json()).code || ""); } catch { /* tom */ }
  code = code.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (code.length !== 6) return json({ error: "Koden ska vara 6 tecken" }, 400);

  const { data: row } = await admin.from("tv_codes")
    .select("code, expires_at, token_hash").eq("code", code).maybeSingle();
  if (!row || row.token_hash || new Date(row.expires_at) < new Date()) {
    return json({ error: "Koden är ogiltig eller har gått ut" }, 404);
  }

  const { data: link, error } = await admin.auth.admin.generateLink({ type: "magiclink", email: user.email });
  if (error || !link?.properties?.hashed_token) return json({ error: "Kunde inte skapa inloggning" }, 500);

  await admin.from("tv_codes")
    .update({ token_hash: link.properties.hashed_token, approved_by: user.id }).eq("code", code);
  return json({ ok: true });
});

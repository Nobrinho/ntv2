// Painel de administração do NBR Play. Fala direto com o Supabase; tudo que é de admin passa pelas
// funções admin_* do banco, que só respondem para um administrador com 2FA confirmado na sessão.
// Não há segredo aqui: a URL e a publishable key são públicas (as mesmas do app).
'use strict';

// Para testar com o Supabase local: admin/?url=http://127.0.0.1:54321&key=<publishable key local>
const params = new URLSearchParams(location.search);
const SUPABASE_URL = params.get('url') || 'https://bqslneydlhhpzhejovod.supabase.co';
const SUPABASE_KEY = params.get('key') || 'sb_publishable__6SDB_CFQf-Jw-iE_1FkAw_j8HHL_KQ';
const sb = window.supabase.createClient(SUPABASE_URL, SUPABASE_KEY, { auth: { persistSession: true, storageKey: 'nbr-admin' } });

const app = document.getElementById('app');
const dlg = document.getElementById('dlg');
const logoutBtn = document.getElementById('logout');
const PAGE = 50;
let tab = 'overview';

// ---------------------------------------------------------------- utilidades
// Todo texto vindo do banco (nomes de aparelho, notas de reporte) passa por aqui antes de virar HTML.
function esc(v) {
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
const money = (c) => 'R$ ' + (Number(c || 0) / 100).toFixed(2).replace('.', ',');
const when = (d) => d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
const day = (d) => d ? new Date(d).toLocaleDateString('pt-BR') : '—';
const short = (id) => id ? String(id).slice(0, 8) : '—';
function ago(d) {
  if (!d) return '—';
  const m = Math.round((Date.now() - new Date(d).getTime()) / 60000);
  if (m < 60) return `há ${Math.max(m, 0)} min`;
  if (m < 1440) return `há ${Math.round(m / 60)} h`;
  return `há ${Math.round(m / 1440)} d`;
}
async function rpc(name, args = {}) {
  const { data, error } = await sb.rpc(name, args);
  if (error) throw new Error(error.message);
  return data;
}
function html(strings, ...vals) {
  return strings.reduce((acc, s, i) => acc + s + (i < vals.length ? vals[i] : ''), '');
}
const BRAND = `<div class="brand"><img src="../logo.png" alt=""><div><b>NBR Play</b><span>Administração</span></div></div>`;
function errorBox(e) { return `<p class="msg err">${esc(e.message || e)}</p>`; }

// ---------------------------------------------------------------- login e 2FA
async function start() {
  const { data: { session } } = await sb.auth.getSession();
  if (!session) return showLogin();
  const { data: aal } = await sb.auth.mfa.getAuthenticatorAssuranceLevel();
  if (aal.currentLevel !== 'aal2') return showMfa();
  const who = await rpc('admin_whoami').catch(() => null);
  if (!who || !who.admin) {
    app.innerHTML = `<div class="card center"><h2>Sem acesso</h2><p>Esta conta não é de administrador.</p>
      <button id="out">Sair</button></div>`;
    document.getElementById('out').onclick = logout;
    return;
  }
  logoutBtn.hidden = false;
  showPanel();
}

function showLogin(msg = '') {
  logoutBtn.hidden = true;
  app.innerHTML = `<form class="card center" id="f">
    ${BRAND}<h2>Entrar</h2>
    <label for="email">E-mail</label><input id="email" type="email" autocomplete="username" required>
    <label for="pw">Senha</label><input id="pw" type="password" autocomplete="current-password" required>
    <div class="row" style="margin-top:14px"><button class="primary grow" type="submit">Entrar</button></div>
    <p class="msg err" id="m">${esc(msg)}</p></form>`;
  document.getElementById('f').onsubmit = async (ev) => {
    ev.preventDefault();
    const { error } = await sb.auth.signInWithPassword({
      email: document.getElementById('email').value.trim(),
      password: document.getElementById('pw').value,
    });
    if (error) return (document.getElementById('m').textContent =
      error.code === 'invalid_credentials' ? 'E-mail ou senha incorretos.' : `Não foi possível entrar: ${error.message}`);
    start();
  };
}

async function showMfa() {
  logoutBtn.hidden = false;
  const { data: factors } = await sb.auth.mfa.listFactors();
  let factor = (factors?.totp || []).find((f) => f.status === 'verified');
  let enrollHtml = '';
  if (!factor) {
    // Primeiro acesso: cadastra o app autenticador (Google Authenticator, Authy, 1Password...).
    for (const f of factors?.all || []) if (f.status !== 'verified') await sb.auth.mfa.unenroll({ factorId: f.id });
    const { data, error } = await sb.auth.mfa.enroll({ factorType: 'totp', friendlyName: 'Admin NBR Play' });
    if (error) { app.innerHTML = `<div class="card center">${errorBox(error)}</div>`; return; }
    factor = data;
    enrollHtml = `<p>Primeiro acesso: leia o QR Code no seu app autenticador e digite o código de 6 dígitos.</p>
      <img class="qr" src="${esc(data.totp.qr_code)}" alt="QR Code do autenticador">
      <p class="mono">Chave manual: ${esc(data.totp.secret)}</p>`;
  }
  app.innerHTML = `<form class="card center" id="f">
    <span class="step-tag">Passo 2 de 2</span><h2>Verificação em duas etapas</h2>${enrollHtml}
    <label for="code">Código do app autenticador</label>
    <input id="code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required>
    <div class="row" style="margin-top:14px"><button class="primary grow" type="submit">Confirmar</button></div>
    <p class="msg err" id="m"></p></form>`;
  document.getElementById('code').focus();
  document.getElementById('f').onsubmit = async (ev) => {
    ev.preventDefault();
    const { error } = await sb.auth.mfa.challengeAndVerify({ factorId: factor.id, code: document.getElementById('code').value.trim() });
    if (error) return (document.getElementById('m').textContent = 'Código inválido. Confira o horário do celular e tente de novo.');
    start();
  };
}

async function logout() {
  await sb.auth.signOut();
  showLogin();
}
logoutBtn.onclick = logout;

// ---------------------------------------------------------------- painel
const TABS = [['overview', 'Visão geral'], ['devices', 'Aparelhos'], ['payments', 'Pagamentos'], ['reports', 'Reportes'], ['audit', 'Ações']];
// Ícones de traço do menu (texto fixo, sem dados do servidor).
const ICONS = {
  overview: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>',
  devices: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2" y="4" width="14" height="10" rx="1.5"/><rect x="17" y="8" width="5" height="12" rx="1.5"/><path d="M6 18h6"/></svg>',
  payments: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/></svg>',
  reports: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 21V4"/><path d="M4 4h12l-2 4 2 4H4"/></svg>',
  audit: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
};

function showPanel() {
  app.innerHTML = `<nav class="tabs" aria-label="Painel">${TABS.map(([id, n]) => `<button data-tab="${id}">${ICONS[id]}<span>${n}</span></button>`).join('')}</nav>
    <div class="content"><h1 id="ptitle"></h1><div id="view"></div></div>`;
  app.querySelectorAll('[data-tab]').forEach((b) => (b.onclick = () => { tab = b.dataset.tab; render(); }));
  render();
}

function render() {
  app.querySelectorAll('[data-tab]').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
  document.getElementById('ptitle').textContent = TABS.find(([id]) => id === tab)[1];
  ({ overview, devices, payments, reports, audit })[tab]();
}
const view = () => document.getElementById('view');

async function overview() {
  view().innerHTML = '<p class="muted">Carregando…</p>';
  try {
    const o = await rpc('admin_overview');
    const s = (n, label) => `<div class="stat"><b>${esc(n)}</b><span>${esc(label)}</span></div>`;
    view().innerHTML = html`
      <div class="card"><h2>Uso</h2><div class="stats">
        ${s(o.active_1d, 'ativos hoje (24 h)')}${s(o.active_7d, 'ativos em 7 dias')}${s(o.active_30d, 'ativos em 30 dias')}
        ${s(o.devices_total, 'aparelhos instalados')}${s(o.linked_accounts, 'contas com aparelhos conectados')}
        ${s(o.linked_devices, 'aparelhos conectados')}${s(o.blocked_devices, 'aparelhos bloqueados')}
      </div></div>
      <div class="card"><h2>Premium ${o.premium_enforced ? '<span class="tag ok">cobrança ligada</span>' : '<span class="tag warn">cobrança desligada</span>'}</h2>
        <div class="stats">
        ${s(o.premium_accounts, 'contas Premium')}${s(o.premium_devices_active_30d, 'aparelhos Premium ativos (30 d)')}
        ${s(o.free_devices_active_30d, 'aparelhos gratuitos ativos (30 d)')}${s(o.payments_30d, 'pagamentos em 30 dias')}
        ${s(money(o.revenue_30d_cents), 'receita em 30 dias')}${s(money(o.revenue_total_cents), 'receita total')}
      </div></div>
      <div class="card"><h2>Reportes</h2><div class="stats">${s(o.reports_open, 'reportes abertos')}</div></div>`;
  } catch (e) { view().innerHTML = errorBox(e); }
}

// ---------------------------------------------------------------- aparelhos
const devState = { segment: 'active', q: '', off: 0 };
async function devices() {
  view().innerHTML = html`<div class="card">
    <div class="row">
      <select id="seg" style="max-width:220px">
        ${[['active', 'Ativos (30 dias)'], ['all', 'Todos'], ['premium', 'Premium'], ['free', 'Gratuitos'], ['linked', 'Conectados'], ['blocked', 'Bloqueados']]
          .map(([v, n]) => `<option value="${v}" ${devState.segment === v ? 'selected' : ''}>${n}</option>`).join('')}
      </select>
      <input id="q" class="grow" placeholder="Buscar por nome, modelo, versão ou id" value="${esc(devState.q)}">
      <button id="go">Buscar</button>
    </div><div id="list" style="margin-top:12px"><p class="muted">Carregando…</p></div></div>`;
  const reload = () => { devState.segment = document.getElementById('seg').value; devState.q = document.getElementById('q').value; devState.off = 0; devices(); };
  document.getElementById('go').onclick = reload;
  document.getElementById('seg').onchange = reload;
  document.getElementById('q').onkeydown = (e) => { if (e.key === 'Enter') reload(); };
  try {
    const r = await rpc('admin_devices', { segment: devState.segment, q: devState.q || null, lim: PAGE, off: devState.off });
    const rows = r.items.map((d) => {
      const premium = d.premium_until && new Date(d.premium_until) > new Date();
      return `<tr class="click" data-acc="${esc(d.account_id)}">
        <td data-label="Aparelho">${esc(d.device_name || d.device_model || 'Aparelho')}<div class="mono">${esc(short(d.user_id))}</div></td>
        <td data-label="Versão">${esc(d.app_version || '—')}</td>
        <td data-label="Última atividade">${esc(ago(d.active_at))}</td>
        <td data-label="Situação">${premium ? `<span class="tag ok">Premium até ${esc(day(d.premium_until))}</span>` : '<span class="tag">Gratuito</span>'}
          ${d.linked ? `<span class="tag acc">${d.is_owner ? 'dono da conta' : 'conectado'}</span>` : ''}
          ${d.blocked_reason !== null && d.blocked_reason !== undefined ? '<span class="tag bad">bloqueado</span>' : ''}</td>
        <td data-label="Instalado">${esc(day(d.created_at))}</td></tr>`;
    }).join('');
    document.getElementById('list').innerHTML = r.total === 0 ? '<p class="muted">Nenhum aparelho.</p>' :
      `<table><thead><tr><th>Aparelho</th><th>Versão</th><th>Última atividade</th><th>Situação</th><th>Instalado</th></tr></thead><tbody>${rows}</tbody></table>
       ${pager(r.total, devState, devices)}`;
    document.querySelectorAll('tr[data-acc]').forEach((tr) => (tr.onclick = () => openAccount(tr.dataset.acc)));
    bindPager(devState, devices);
  } catch (e) { document.getElementById('list').innerHTML = errorBox(e); }
}

function pager(total, state) {
  const from = total === 0 ? 0 : state.off + 1;
  const to = Math.min(state.off + PAGE, total);
  return `<div class="pager">${from}–${to} de ${total}
    <button data-prev ${state.off === 0 ? 'disabled' : ''}>Anterior</button>
    <button data-next ${to >= total ? 'disabled' : ''}>Próxima</button></div>`;
}
function bindPager(state, fn) {
  const p = view().querySelector('[data-prev]'); const n = view().querySelector('[data-next]');
  if (p) p.onclick = () => { state.off = Math.max(0, state.off - PAGE); fn(); };
  if (n) n.onclick = () => { state.off += PAGE; fn(); };
}

// Detalhe da conta (todos os aparelhos que compartilham dados e Premium) com as ações.
async function openAccount(accountId) {
  dlg.innerHTML = '<p class="muted">Carregando…</p>';
  dlg.showModal();
  try {
    const a = await rpc('admin_account', { account_id: accountId });
    const premium = a.premium_until && new Date(a.premium_until) > new Date();
    // Conta de um aparelho só não tem membros: o próprio usuário é a conta.
    const devs = a.devices.length ? a.devices : [{ user_id: accountId, device_name: null, blocked: false }];
    dlg.innerHTML = html`<div class="row"><h2 class="grow" style="margin:0">Conta ${esc(short(accountId))}</h2><button data-close>Fechar</button></div>
      <p class="mono">${esc(accountId)}</p>
      <p>${premium ? `<span class="tag ok">Premium até ${esc(day(a.premium_until))}</span>` : '<span class="tag">Gratuito</span>'}
         · ${esc(a.favorites)} na Minha lista · ${esc(a.history)} no histórico</p>
      <h2>Aparelhos</h2>
      <table><tbody>${devs.map((d) => `<tr>
        <td data-label="Aparelho">${esc(d.device_name || 'Aparelho')}<div class="mono">${esc(short(d.user_id))}</div></td>
        <td data-label="Atividade">${esc(ago(d.active_at))}</td>
        <td><div class="row">
          ${d.blocked ? `<button data-unblock="${esc(d.user_id)}">Desbloquear</button>` : `<button class="danger" data-block="${esc(d.user_id)}">Bloquear</button>`}
          ${a.devices.length > 1 ? `<button data-disconnect="${esc(d.user_id)}">Desconectar da conta</button>` : ''}
        </div></td></tr>`).join('')}</tbody></table>
      <h2 style="margin-top:16px">Premium de cortesia</h2>
      <div class="row"><input id="days" type="number" value="30" min="-3650" max="3650" style="max-width:110px">
        <input id="note" class="grow" placeholder="Motivo (opcional)"><button class="primary" data-grant>Dar dias</button></div>
      <p class="muted" style="font-size:13px">Dias negativos removem uma cortesia dada por engano.</p>
      <h2 style="margin-top:16px">Pagamentos</h2>
      ${a.payments.length ? `<table><tbody>${a.payments.map((p) => `<tr><td data-label="Data">${esc(when(p.created_at))}</td>
        <td data-label="Plano">${esc(p.plan_id)}</td><td data-label="Valor">${esc(money(p.amount_cents))}</td>
        <td data-label="Situação">${statusTag(p.status)}</td></tr>`).join('')}</tbody></table>` : '<p class="muted">Nenhum pagamento.</p>'}
      <p class="msg" id="am"></p>`;
    const done = (text) => { const m = document.getElementById('am'); m.className = 'msg ok'; m.textContent = text; };
    // Confirmação dentro do painel (os diálogos nativos do navegador ficam ruins no celular).
    const ask = (text, withReason, onYes) => {
      const m = document.getElementById('am');
      m.className = 'msg';
      m.innerHTML = `<div class="card" style="margin:0"><p style="margin:0 0 8px">${esc(text)}</p>
        ${withReason ? '<input id="reason" placeholder="Motivo (fica no registro de ações)" value="abuso" style="margin-bottom:8px">' : ''}
        <div class="row"><button class="primary" data-yes>Confirmar</button><button data-no>Cancelar</button></div></div>`;
      m.querySelector('[data-no]').onclick = () => { m.innerHTML = ''; };
      m.querySelector('[data-yes]').onclick = () => onYes(withReason ? document.getElementById('reason').value : null);
    };
    const fail = (e) => { const m = document.getElementById('am'); m.className = 'msg err'; m.textContent = e.message; };
    dlg.querySelector('[data-close]').onclick = () => { dlg.close(); if (tab === 'devices') devices(); };
    dlg.querySelector('[data-grant]').onclick = async () => {
      const days = parseInt(document.getElementById('days').value, 10);
      if (!days) return;
      const note = document.getElementById('note').value || null;
      ask(`${days > 0 ? 'Dar' : 'Remover'} ${Math.abs(days)} dias de Premium para esta conta?`, false, async () => {
        try { const r = await rpc('admin_grant_premium', { account_id: accountId, days, note });
          done(`Premium até ${day(r.premium_until)}.`); setTimeout(() => openAccount(accountId), 900); } catch (e) { fail(e); }
      });
    };
    dlg.querySelectorAll('[data-block]').forEach((b) => (b.onclick = () => ask(
      'Bloquear este aparelho? Ele para de sincronizar, buscar e reportar até ser desbloqueado.', true, async (reason) => {
        try { await rpc('admin_block_device', { user_id: b.dataset.block, reason }); openAccount(accountId); } catch (e) { fail(e); }
      })));
    dlg.querySelectorAll('[data-unblock]').forEach((b) => (b.onclick = async () => {
      try { await rpc('admin_unblock_device', { user_id: b.dataset.unblock }); openAccount(accountId); } catch (e) { fail(e); }
    }));
    dlg.querySelectorAll('[data-disconnect]').forEach((b) => (b.onclick = () => ask(
      'Desconectar este aparelho da conta? Ele fica com uma cópia dos dados.', false, async () => {
        try { await rpc('admin_disconnect_device', { user_id: b.dataset.disconnect }); openAccount(accountId); } catch (e) { fail(e); }
      })));
  } catch (e) { dlg.innerHTML = errorBox(e) + '<button data-close>Fechar</button>'; dlg.querySelector('[data-close]').onclick = () => dlg.close(); }
}

function statusTag(s) {
  const cls = { approved: 'ok', pending: 'warn', creating: 'warn', open: 'warn', resolved: 'ok', refunded: 'bad', failed: 'bad', rejected: 'bad' }[s] || '';
  const label = { approved: 'aprovado', pending: 'pendente', creating: 'criando', expired: 'expirado', cancelled: 'cancelado',
                  rejected: 'recusado', refunded: 'estornado', failed: 'falhou', open: 'aberto', resolved: 'resolvido', ignored: 'ignorado' }[s] || s;
  return `<span class="tag ${cls}">${esc(label)}</span>`;
}

// ---------------------------------------------------------------- pagamentos
const payState = { status: '', off: 0 };
async function payments() {
  view().innerHTML = html`<div class="card"><div class="row">
      <select id="st" style="max-width:220px">${[['', 'Todos'], ['approved', 'Aprovados'], ['pending', 'Pendentes'], ['expired', 'Expirados'], ['refunded', 'Estornados'], ['failed', 'Com problema']]
        .map(([v, n]) => `<option value="${v}" ${payState.status === v ? 'selected' : ''}>${n}</option>`).join('')}</select></div>
    <div id="list" style="margin-top:12px"><p class="muted">Carregando…</p></div></div>`;
  document.getElementById('st').onchange = (e) => { payState.status = e.target.value; payState.off = 0; payments(); };
  try {
    const r = await rpc('admin_payments', { status: payState.status || null, lim: PAGE, off: payState.off });
    document.getElementById('list').innerHTML = r.total === 0 ? '<p class="muted">Nenhum pagamento.</p>' :
      `<table><thead><tr><th>Data</th><th>Conta</th><th>Plano</th><th>Valor</th><th>Situação</th><th>Mercado Pago</th></tr></thead><tbody>
      ${r.items.map((p) => `<tr class="click" data-acc="${esc(p.account_id)}"><td data-label="Data">${esc(when(p.created_at))}</td>
        <td data-label="Conta" class="mono">${esc(short(p.account_id))}</td><td data-label="Plano">${esc(p.plan_id)}</td>
        <td data-label="Valor">${esc(money(p.amount_cents))}</td><td data-label="Situação">${statusTag(p.status)}</td>
        <td data-label="Mercado Pago" class="mono">${esc(p.provider_payment_id || '—')}</td></tr>`).join('')}
      </tbody></table>${pager(r.total, payState)}`;
    document.querySelectorAll('tr[data-acc]').forEach((tr) => (tr.onclick = () => openAccount(tr.dataset.acc)));
    bindPager(payState, payments);
  } catch (e) { document.getElementById('list').innerHTML = errorBox(e); }
}

// ---------------------------------------------------------------- reportes
const REASONS = { wrong_video: 'Vídeo errado', not_dubbed: 'Não está dublado', audio_problem: 'Problema no áudio',
  subtitle_problem: 'Problema na legenda', bad_quality: 'Qualidade ruim', does_not_play: 'Não reproduz', other: 'Outro problema' };
const repState = { status: 'open', off: 0 };
async function reports() {
  view().innerHTML = html`<div class="card"><div class="row">
      <select id="st" style="max-width:200px">${[['open', 'Abertos'], ['resolved', 'Resolvidos'], ['ignored', 'Ignorados'], ['', 'Todos']]
        .map(([v, n]) => `<option value="${v}" ${repState.status === v ? 'selected' : ''}>${n}</option>`).join('')}</select>
      <span class="grow"></span>
      <button data-set="resolved" disabled>Marcar como resolvido</button><button data-set="ignored" disabled>Ignorar</button>
      <button data-set="open" disabled>Reabrir</button></div>
    <div id="list" style="margin-top:12px"><p class="muted">Carregando…</p></div></div>`;
  document.getElementById('st').onchange = (e) => { repState.status = e.target.value; repState.off = 0; reports(); };
  try {
    const r = await rpc('admin_reports', { status: repState.status || null, lim: PAGE, off: repState.off });
    document.getElementById('list').innerHTML = r.total === 0 ? '<p class="muted">Nenhum reporte.</p>' :
      `<table><thead><tr><th><input type="checkbox" id="all" aria-label="Selecionar todos"></th><th>Filme</th><th>Motivo</th><th>Aparelho</th><th>Data</th><th>Situação</th></tr></thead><tbody>
      ${r.items.map((x) => `<tr><td><input type="checkbox" data-id="${esc(x.id)}" aria-label="Selecionar"></td>
        <td data-label="Filme"><b>${esc(x.title || x.media_id)}</b>${x.year ? ` (${esc(x.year)})` : ''}
          <div class="muted">${esc(x.channel_name || '')} · <span class="mono">${esc(x.media_id)}</span></div>
          ${x.open_for_media > 1 ? `<span class="tag warn">${esc(x.open_for_media)} reportes abertos deste filme</span>` : ''}</td>
        <td data-label="Motivo">${esc(REASONS[x.reason] || x.reason)}${x.note ? `<div class="muted">"${esc(x.note)}"</div>` : ''}</td>
        <td data-label="Aparelho">${esc(x.device || '—')}<div class="muted">${esc(x.app_version || '')}</div></td>
        <td data-label="Data">${esc(when(x.created_at))}</td><td data-label="Situação">${statusTag(x.status)}</td></tr>`).join('')}
      </tbody></table>${pager(r.total, repState)}`;
    const boxes = () => [...document.querySelectorAll('input[data-id]')];
    const sync = () => { const any = boxes().some((b) => b.checked); view().querySelectorAll('[data-set]').forEach((b) => (b.disabled = !any)); };
    boxes().forEach((b) => (b.onchange = sync));
    const all = document.getElementById('all');
    if (all) all.onchange = () => { boxes().forEach((b) => (b.checked = all.checked)); sync(); };
    view().querySelectorAll('[data-set]').forEach((btn) => (btn.onclick = async () => {
      const ids = boxes().filter((b) => b.checked).map((b) => Number(b.dataset.id));
      try { await rpc('admin_set_report_status', { ids, status: btn.dataset.set }); reports(); }
      catch (e) { document.getElementById('list').insertAdjacentHTML('afterbegin', errorBox(e)); }
    }));
    bindPager(repState, reports);
  } catch (e) { document.getElementById('list').innerHTML = errorBox(e); }
}

// ---------------------------------------------------------------- auditoria
const ACTIONS = { grant_premium: 'Premium de cortesia', report_resolved: 'Reportes resolvidos', report_ignored: 'Reportes ignorados',
  report_open: 'Reportes reabertos', block_device: 'Bloqueio', unblock_device: 'Desbloqueio', disconnect_device: 'Desconexão' };
async function audit() {
  view().innerHTML = '<div class="card"><p class="muted">Carregando…</p></div>';
  try {
    const items = await rpc('admin_actions_log', { lim: 100 });
    view().innerHTML = `<div class="card"><h2>Últimas ações de administrador</h2>${items.length === 0 ? '<p class="muted">Nenhuma ação ainda.</p>' :
      `<table><thead><tr><th>Data</th><th>Ação</th><th>Alvo</th><th>Detalhes</th></tr></thead><tbody>
      ${items.map((a) => `<tr><td data-label="Data">${esc(when(a.created_at))}</td><td data-label="Ação">${esc(ACTIONS[a.action] || a.action)}</td>
        <td data-label="Alvo" class="mono">${esc(a.target || '—')}</td><td data-label="Detalhes" class="mono">${esc(a.details ? JSON.stringify(a.details) : '')}</td></tr>`).join('')}
      </tbody></table>`}</div>`;
  } catch (e) { view().innerHTML = errorBox(e); }
}

start().catch((e) => { app.innerHTML = `<div class="card center">${errorBox(e)}</div>`; });

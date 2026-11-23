// Painel de administração do NBR Play. Fala direto com o Supabase; tudo que é de admin passa pelas
// funções admin_* do banco, que só respondem para um administrador com 2FA confirmado na sessão.
// Não há segredo aqui: a URL e a publishable key são públicas (as mesmas do app).
'use strict';

// Para testar com o Supabase local: admin/?url=http://127.0.0.1:54321&key=<publishable key local>
// Só vale com a página aberta localmente: no site publicado, um link com ?url= mandaria e-mail,
// senha e código 2FA do administrador para outro servidor.
const params = new URLSearchParams(location.search);
const LOCAL = ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
const SUPABASE_URL = (LOCAL && params.get('url')) || 'https://bqslneydlhhpzhejovod.supabase.co';
const SUPABASE_KEY = (LOCAL && params.get('key')) || 'sb_publishable__6SDB_CFQf-Jw-iE_1FkAw_j8HHL_KQ';
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
const TABS = [['overview', 'Visão geral'], ['devices', 'Aparelhos'], ['payments', 'Pagamentos'], ['reports', 'Denúncias'], ['audit', 'Ações']];
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
        <td data-label="Aparelho">${esc(d.device_name || d.device_model || 'Aparelho')}<div class="mono">${esc(short(d.user_id))}</div>
          <div class="muted">${esc([d.device_model, d.app_version].filter(Boolean).join(' · '))}</div></td>
        <td data-label="Atividade">${esc(ago(d.active_at))}
          ${d.blocked ? `<div><span class="tag bad">bloqueado ${esc(day(d.blocked_at))}</span></div>
            <div class="muted">${esc(d.blocked_reason || 'sem motivo')}</div>` : ''}</td>
        <td><div class="row">
          ${d.blocked ? `<button data-unblock="${esc(d.user_id)}">Desbloquear</button>` : `<button class="danger" data-block="${esc(d.user_id)}">Bloquear</button>`}
          ${a.devices.length > 1 ? `<button data-disconnect="${esc(d.user_id)}">Desconectar da conta</button>` : ''}
        </div></td></tr>`).join('')}</tbody></table>
      <h2 style="margin-top:16px">Perfis</h2>
      ${(a.profiles || []).length ? `<table><tbody>${a.profiles.map((p) => `<tr class="click" data-profile="${esc(p.profile_id)}">
        <td data-label="Perfil"><b>${esc(p.name)}</b> ${p.kids ? `<span class="tag acc">infantil${p.max_rating ? ' · até ' + esc(p.max_rating) : ''}</span>` : ''}
          ${p.has_pin ? '<span class="tag">com PIN</span>' : ''}<div class="mono">${esc(p.profile_id)}</div></td>
        <td data-label="Minha lista">${esc(p.favorites)} na lista</td><td data-label="Histórico">${esc(p.history)} no histórico</td>
        <td data-label="Alterado">${esc(when(p.updated_at))}</td></tr>`).join('')}</tbody></table>
        <p class="muted" style="font-size:13px">Clique num perfil para ver a lista, o histórico e o progresso dele.</p>`
        : '<p class="muted">Nenhum perfil sincronizado (aparelho em versão antiga ou sem Premium de sincronização).</p>'}
      <div id="items"></div>
      <h2 style="margin-top:16px">Denúncias desta conta</h2>
      ${(a.reports || []).length ? `<table><tbody>${a.reports.map((r) => `<tr>
        <td data-label="Título"><b>${esc(r.title || r.media_id)}</b><div class="mono">${esc(r.media_id)}</div></td>
        <td data-label="Motivo">${esc(REASONS[r.reason] || r.reason)}${r.note ? `<div class="muted">"${esc(r.note)}"</div>` : ''}</td>
        <td data-label="Data">${esc(when(r.created_at))}</td><td data-label="Situação">${statusTag(r.status)}</td></tr>`).join('')}</tbody></table>`
        : '<p class="muted">Nenhuma denúncia.</p>'}
      <h2 style="margin-top:16px">Premium de cortesia</h2>
      <div class="row"><input id="days" type="number" value="30" min="-3650" max="3650" style="max-width:110px">
        <input id="note" class="grow" placeholder="Motivo (opcional)"><button class="primary" data-grant>Dar dias</button></div>
      <p class="muted" style="font-size:13px">Dias negativos removem uma cortesia dada por engano.</p>
      <h2 style="margin-top:16px">Pagamentos</h2>
      ${a.payments.length ? `<table><tbody>${a.payments.map((p) => `<tr><td data-label="Data">${esc(when(p.created_at))}</td>
        <td data-label="Plano">${esc(p.plan_id)}</td><td data-label="Valor">${esc(money(p.amount_cents))}</td>
        <td data-label="Situação">${statusTag(p.status)}</td></tr>`).join('')}</tbody></table>` : '<p class="muted">Nenhum pagamento.</p>'}
      <h2 style="margin-top:16px">Dados pessoais (LGPD)</h2>
      <p class="muted" style="font-size:13px">Apaga perfis, Minha lista, histórico e progresso da conta e tira o vínculo das denúncias.
        Aparelhos, Premium e pagamentos continuam.</p>
      <button class="danger" data-wipe>Apagar dados da conta</button>
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
    dlg.querySelectorAll('[data-profile]').forEach((tr) => (tr.onclick = () => showItems(accountId, tr.dataset.profile, 'favorites')));
    // Dois passos: motivo e depois digitar APAGAR (não tem como desfazer).
    dlg.querySelector('[data-wipe]').onclick = () => {
      const m = document.getElementById('am');
      m.className = 'msg';
      m.innerHTML = `<div class="card" style="margin:0"><p style="margin:0 0 8px"><b>Não tem como desfazer.</b>
        Os aparelhos desta conta continuam, mas voltam sem perfis, lista e histórico.</p>
        <input id="wreason" placeholder="Motivo (ex.: pedido por e-mail em 23/11)" style="margin-bottom:8px">
        <input id="wconfirm" placeholder="Digite APAGAR para confirmar" style="margin-bottom:8px">
        <div class="row"><button class="danger" data-yes>Apagar dados</button><button data-no>Cancelar</button></div></div>`;
      m.querySelector('[data-no]').onclick = () => { m.innerHTML = ''; };
      m.querySelector('[data-yes]').onclick = async () => {
        const reason = document.getElementById('wreason').value.trim();
        if (!reason) return fail(new Error('Informe o motivo.'));
        if (document.getElementById('wconfirm').value.trim() !== 'APAGAR') return fail(new Error('Digite APAGAR para confirmar.'));
        try {
          const r = await rpc('admin_delete_account_data', { account_id: accountId, reason });
          done(`Apagados: ${r.profiles} perfis, ${r.favorites} na lista, ${r.history} no histórico, ${r.progress} progressos; ` +
            `${r.reports_unlinked} denúncias sem vínculo.`);
          setTimeout(() => openAccount(accountId), 1500);
        } catch (e) { fail(e); }
      };
    };
  } catch (e) { dlg.innerHTML = errorBox(e) + '<button data-close>Fechar</button>'; dlg.querySelector('[data-close]').onclick = () => dlg.close(); }
}

// Lista, histórico ou progresso de um perfil, dentro do detalhe da conta.
const ITEM_KINDS = [['favorites', 'Minha lista'], ['history', 'Histórico'], ['progress', 'Progresso']];
async function showItems(accountId, profileId, kind) {
  const box = document.getElementById('items');
  if (!box) return;
  box.innerHTML = '<p class="muted">Carregando…</p>';
  try {
    const items = await rpc('admin_account_items', { account_id: accountId, profile_id: profileId, kind, lim: 100, off: 0 });
    const row = (x) => kind === 'progress'
      ? `<tr><td data-label="Título" class="mono">${esc(x.media_id)}</td>
          <td data-label="Posição">${esc(Math.round((x.position_ms || 0) / 60000))} de ${esc(Math.round((x.duration_ms || 0) / 60000))} min</td>
          <td data-label="Data">${esc(when(x.updated_at))}</td></tr>`
      : `<tr><td data-label="Título"><b>${esc(x.title || x.media_id)}</b><div class="mono">${esc(x.media_id)}</div></td>
          <td data-label="Canal">${esc(x.channel_name || '—')}</td>
          <td data-label="Data">${esc(when(x.updated_at || x.last_opened_at))}</td></tr>`;
    box.innerHTML = `<div class="card" style="margin:12px 0 0"><div class="row">
        <b class="grow">Perfil <span class="mono">${esc(profileId)}</span></b>
        ${ITEM_KINDS.map(([k, n]) => `<button data-kind="${k}" ${k === kind ? 'class="primary"' : ''}>${n}</button>`).join('')}</div>
      ${items.length ? `<table><tbody>${items.map(row).join('')}</tbody></table>` : '<p class="muted">Vazio.</p>'}</div>`;
    box.querySelectorAll('[data-kind]').forEach((b) => (b.onclick = () => showItems(accountId, profileId, b.dataset.kind)));
  } catch (e) { box.innerHTML = errorBox(e); }
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
// Triagem por título: um cartão por filme/episódio com as denúncias dele, contagem por motivo e as ações
// (responder e resolver, ignorar, ocultar o título, pedir repostagem ao bot).
const repState = { status: 'open', reason: '', off: 0 };
async function reports() {
  view().innerHTML = html`<div class="card"><div class="row">
      <select id="st" style="max-width:200px">${[['open', 'Abertas'], ['resolved', 'Resolvidas'], ['ignored', 'Ignoradas'], ['', 'Todas']]
        .map(([v, n]) => `<option value="${v}" ${repState.status === v ? 'selected' : ''}>${n}</option>`).join('')}</select>
      <select id="rs" style="max-width:220px"><option value="">Todos os motivos</option>${Object.entries(REASONS)
        .map(([v, n]) => `<option value="${v}" ${repState.reason === v ? 'selected' : ''}>${esc(n)}</option>`).join('')}</select>
      <span class="grow"></span><button id="hiddenBtn">Títulos ocultos</button></div>
    <div id="list" style="margin-top:12px"><p class="muted">Carregando…</p></div></div>`;
  document.getElementById('st').onchange = (e) => { repState.status = e.target.value; repState.off = 0; reports(); };
  document.getElementById('rs').onchange = (e) => { repState.reason = e.target.value; repState.off = 0; reports(); };
  document.getElementById('hiddenBtn').onclick = hiddenTitles;
  try {
    const r = await rpc('admin_reports_grouped', { status: repState.status || null, reason: repState.reason || null, lim: PAGE, off: repState.off });
    const list = document.getElementById('list');
    if (r.total === 0) { list.innerHTML = '<p class="muted">Nenhuma denúncia.</p>'; return; }
    list.innerHTML = r.items.map((g, i) => `<div class="card" style="margin:0 0 12px" data-group="${i}">
      <div class="row"><b class="grow">${esc(g.title)}${g.year ? ` (${esc(g.year)})` : ''}</b>
        <span class="tag ${g.total >= 3 ? 'bad' : 'warn'}">${esc(g.total)} denúncia(s)</span>
        ${g.hidden ? '<span class="tag bad">oculto</span>' : ''}</div>
      <div class="muted">${esc(g.channel_name || '')} · <span class="mono">${esc(g.ntv_id || g.media_id)}</span> · última ${esc(ago(g.last_at))}</div>
      <p style="margin:8px 0">${Object.entries(g.reasons || {}).map(([k, n]) => `<span class="tag">${esc(REASONS[k] || k)}: ${esc(n)}</span>`).join('')}</p>
      <table><tbody>${(g.reports || []).map((x) => `<tr>
        <td data-label="Motivo">${esc(REASONS[x.reason] || x.reason)}${x.note ? `<div class="muted">"${esc(x.note)}"</div>` : ''}
          ${x.admin_note ? `<div class="muted">Resposta: ${esc(x.admin_note)}</div>` : ''}</td>
        <td data-label="Aparelho">${esc(x.device || '—')}<div class="muted">${esc(x.app_version || '')}</div></td>
        <td data-label="Data">${esc(when(x.created_at))}</td><td data-label="Situação">${statusTag(x.status)}</td>
        <td>${x.account_id ? `<button data-acc="${esc(x.account_id)}">Ver conta</button>` : '<span class="muted">sem vínculo</span>'}</td></tr>`).join('')}
      </tbody></table>
      <div class="row" style="margin-top:10px">
        <input class="grow" data-note placeholder="Resposta ao usuário (aparece em Minha conta)">
        <button class="primary" data-act="resolved">Resolver</button><button data-act="ignored">Ignorar</button>
        <button data-act="open">Reabrir</button></div>
      <div class="row" style="margin-top:8px">
        ${g.ntv_id ? (g.hidden ? '<button data-unhide>Mostrar de novo</button>' : '<button class="danger" data-hide>Ocultar título</button>') : ''}
        <button data-repost>Pedir repostagem ao bot</button></div>
      <p class="msg" data-msg></p></div>`).join('') + pager(r.total, repState);
    list.querySelectorAll('[data-acc]').forEach((b) => (b.onclick = () => openAccount(b.dataset.acc)));
    list.querySelectorAll('[data-group]').forEach((card) => {
      const g = r.items[Number(card.dataset.group)];
      const msg = card.querySelector('[data-msg]');
      const ok = (t) => { msg.className = 'msg ok'; msg.textContent = t; };
      const bad = (e) => { msg.className = 'msg err'; msg.textContent = e.message || e; };
      card.querySelectorAll('[data-act]').forEach((btn) => (btn.onclick = async () => {
        const note = card.querySelector('[data-note]').value.trim() || null;
        try { await rpc('admin_set_report_status', { ids: g.ids, status: btn.dataset.act, note }); reports(); } catch (e) { bad(e); }
      }));
      const hide = card.querySelector('[data-hide]');
      if (hide) hide.onclick = async () => {
        const reason = card.querySelector('[data-note]').value.trim() || 'denúncias';
        try { await rpc('admin_hide_title', { ntv_id: g.ntv_id, reason }); reports(); } catch (e) { bad(e); }
      };
      const unhide = card.querySelector('[data-unhide]');
      if (unhide) unhide.onclick = async () => {
        try { await rpc('admin_unhide_title', { ntv_id: g.ntv_id }); reports(); } catch (e) { bad(e); }
      };
      card.querySelector('[data-repost]').onclick = async () => {
        const top = Object.entries(g.reasons || {}).sort((a, b) => b[1] - a[1])[0];
        try {
          const res = await rpc('admin_request_repost', { ntv_id: g.ntv_id || null, media_id: g.media_id,
            reason: top ? top[0] : 'other', note: card.querySelector('[data-note]').value.trim() || null });
          ok(`Pedido #${res.id} enviado ao bot.`);
        } catch (e) { bad(e); }
      };
    });
    bindPager(repState, reports);
  } catch (e) { document.getElementById('list').innerHTML = errorBox(e); }
}

async function hiddenTitles() {
  const list = document.getElementById('list');
  list.innerHTML = '<p class="muted">Carregando…</p>';
  try {
    const items = await rpc('admin_hidden_titles');
    list.innerHTML = items.length === 0 ? '<p class="muted">Nenhum título oculto.</p>' :
      `<table><tbody>${items.map((h) => `<tr><td data-label="Título"><b>${esc(h.title || h.ntv_id)}</b>
        <div class="mono">${esc(h.ntv_id)} · ${esc(h.kind)}</div></td><td data-label="Motivo">${esc(h.reason || '—')}</td>
        <td data-label="Desde">${esc(when(h.hidden_at))}</td><td><button data-show="${esc(h.ntv_id)}">Mostrar de novo</button></td></tr>`).join('')}
      </tbody></table>`;
    list.querySelectorAll('[data-show]').forEach((b) => (b.onclick = async () => {
      try { await rpc('admin_unhide_title', { ntv_id: b.dataset.show }); hiddenTitles(); }
      catch (e) { list.insertAdjacentHTML('afterbegin', errorBox(e)); }
    }));
  } catch (e) { list.innerHTML = errorBox(e); }
}

// ---------------------------------------------------------------- auditoria
const ACTIONS = { grant_premium: 'Premium de cortesia', report_resolved: 'Reportes resolvidos', report_ignored: 'Reportes ignorados',
  report_open: 'Reportes reabertos', block_device: 'Bloqueio', unblock_device: 'Desbloqueio', disconnect_device: 'Desconexão',
  delete_account_data: 'Dados apagados (LGPD)', hide_title: 'Título oculto', unhide_title: 'Título visível de novo',
  request_repost: 'Pedido de repostagem' };
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

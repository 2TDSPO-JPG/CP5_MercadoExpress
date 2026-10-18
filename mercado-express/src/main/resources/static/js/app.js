// Funções compartilhadas pelas páginas do Mercado Express
const money = v => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(v || 0));
const num = v => new Intl.NumberFormat('pt-BR').format(Number(v || 0));

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const STATUS_CLASS = {
  CONFIRMADO: 'b-ok', APROVADO: 'b-ok', ENVIADO: 'b-ok',
  PENDENTE: 'b-warn',
  CANCELADO: 'b-bad', RECUSADO: 'b-bad', ERRO: 'b-bad', ERRO_PAGAMENTO: 'b-bad', FALHOU: 'b-bad'
};
function badge(status) {
  if (!status) return '<span class="badge b-muted">—</span>';
  return `<span class="badge ${STATUS_CLASS[status] || 'b-info'}">${esc(status)}</span>`;
}

// Token CSRF gerado pelo Spring Security e colocado nas páginas (<meta name="_csrf">)
function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content;
  return token && header ? { [header]: token } : {};
}

async function api(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = { 'Content-Type': 'application/json', ...(method !== 'GET' ? csrfHeaders() : {}), ...(options.headers || {}) };
  const r = await fetch(url, { ...options, headers, credentials: 'same-origin' });
  if (r.status === 401) { window.location.href = '/login'; throw new Error('Sessão expirada. Faça login novamente.'); }
  if (r.status === 204) return null;
  let body = null;
  try { body = await r.json(); } catch (_) { /* sem corpo */ }
  if (r.status === 403) throw new Error((body && body.erro) || 'Acesso negado para o seu perfil.');
  if (!r.ok) throw new Error((body && body.erro) || `Erro HTTP ${r.status}`);
  return body;
}

// Mostra o usuário logado no topo e libera/oculta itens por perfil
let usuarioAtual = null;
async function carregarUsuario() {
  try {
    const r = await fetch('/api/me', { credentials: 'same-origin' });
    usuarioAtual = await r.json();
  } catch (_) { usuarioAtual = { autenticado: false }; }
  const b = document.body;
  b.classList.toggle('is-auth', !!usuarioAtual.autenticado);
  b.classList.toggle('is-guest', !usuarioAtual.autenticado);
  b.classList.toggle('is-admin', usuarioAtual.perfil === 'ADMIN');
  const who = document.getElementById('user-who');
  if (who && usuarioAtual.autenticado) {
    who.innerHTML = `${esc(usuarioAtual.nome)}<small>${usuarioAtual.perfil === 'ADMIN' ? 'Gerência' : 'Cliente'} · ${esc(usuarioAtual.email)}</small>`;
  }
  return usuarioAtual;
}
const usuarioPronto = carregarUsuario();

let toastTimer;
function toast(msg, isError = false) {
  let el = document.getElementById('toast');
  if (!el) { el = document.createElement('div'); el.id = 'toast'; el.className = 'toast'; document.body.appendChild(el); }
  el.textContent = msg;
  el.classList.toggle('err', isError);
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3500);
}

function fmtData(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return isNaN(d) ? esc(iso) : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

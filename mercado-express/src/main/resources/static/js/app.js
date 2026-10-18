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

async function api(url, options = {}) {
  const opts = { headers: { 'Content-Type': 'application/json' }, ...options };
  const r = await fetch(url, opts);
  if (r.status === 204) return null;
  let body = null;
  try { body = await r.json(); } catch (_) { /* sem corpo */ }
  if (!r.ok) throw new Error((body && body.erro) || `Erro HTTP ${r.status}`);
  return body;
}

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

const elements = {
  baseUrl: document.querySelector('#base-url'),
  checkoutLink: document.querySelector('#checkout-link'),
  configuration: document.querySelector('#configuration-status'),
  connection: document.querySelector('#connection-label'),
  results: document.querySelector('#results'),
  runner: document.querySelector('#runner-status'),
  themeToggle: document.querySelector('#theme-toggle')
};

const state = {customerId: '', paymentId: '', checkoutSessionId: '', refundId: ''};

function baseUrl() {
  return elements.baseUrl.value.trim().replace(/\/$/, '');
}

function jsonPost(body) {
  return {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)};
}

function setRunner(label, kind = '') {
  elements.runner.className = `runner-status ${kind}`.trim();
  elements.runner.innerHTML = `<i aria-hidden="true"></i> ${label}`;
}

function methodBadge(method) {
  return `<span class="method ${method.toLowerCase()}">${method}</span>`;
}

function showResult({method, path, response, body, duration, error}) {
  elements.results.querySelector('.empty-result')?.remove();
  const item = document.createElement('li');
  const successful = response?.ok;
  item.className = `result ${successful ? 'success' : 'failure'}`;
  const status = response ? `${response.status} ${response.statusText}` : 'NETWORK ERROR';
  const renderedBody = error ? {error: error.message} : body;
  item.innerHTML = `
    <div class="result-head">
      ${methodBadge(method)}
      <span class="result-path">${escapeHtml(path)}</span>
      <span class="result-status">${escapeHtml(status)}</span>
    </div>
    <div class="result-meta"><span>${escapeHtml(new Date().toLocaleTimeString())}</span><span>${duration} ms</span></div>
    <pre>${escapeHtml(JSON.stringify(renderedBody, null, 2) ?? '(empty response)')}</pre>`;
  elements.results.prepend(item);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

async function request(path, options = {}, trigger) {
  const method = options.method || 'GET';
  const started = performance.now();
  if (trigger) trigger.disabled = true;
  setRunner(`${method} ${path}`, 'busy');
  try {
    const response = await fetch(`${baseUrl()}${path}`, options);
    const contentType = response.headers.get('content-type') || '';
    const body = contentType.includes('json') ? await response.json() : await response.text();
    showResult({method, path, response, body, duration: Math.round(performance.now() - started)});
    setRunner(response.ok ? 'Request complete' : 'Request failed', response.ok ? '' : 'error');
    return {response, body};
  } catch (error) {
    showResult({method, path, error, duration: Math.round(performance.now() - started)});
    setRunner('Connection failed', 'error');
    return null;
  } finally {
    if (trigger) trigger.disabled = false;
  }
}

function nullable(value) {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function formData(form) {
  return Object.fromEntries(new FormData(form));
}

function setState(key, value) {
  state[key] = value || '';
  const labels = {
    customerId: ['state-customer', 'Customer'],
    paymentId: ['state-payment', 'Payment'],
    checkoutSessionId: ['state-checkout', 'Checkout'],
    refundId: ['state-refund', 'Refund']
  };
  const [elementId, label] = labels[key];
  const chip = document.querySelector(`#${elementId}`);
  chip.textContent = `${label}: ${state[key] || 'empty'}`;
  chip.title = state[key];
  chip.classList.toggle('filled', Boolean(state[key]));
}

function syncInputs() {
  if (state.customerId) {
    document.querySelector('#customer-get-form [name="id"]').value = state.customerId;
    document.querySelector('#checkout-form [name="stripeCustomerId"]').value = state.customerId;
  }
  if (state.paymentId) {
    document.querySelector('#payment-form [name="id"]').value = state.paymentId;
    document.querySelector('#refund-form [name="paymentId"]').value = state.paymentId;
  }
  if (state.refundId) document.querySelector('#refund-get-form [name="id"]').value = state.refundId;
}

function newReferences() {
  const suffix = `${Date.now()}`;
  document.querySelector('#checkout-form [name="businessReference"]').value = `order-console-${suffix}`;
  document.querySelector('#checkout-form [name="attemptReference"]').value = `attempt-${suffix}`;
  document.querySelector('#refund-form [name="reference"]').value = `refund-${suffix}`;
}

async function checkConfiguration(trigger) {
  const result = await request('/api/configuration', {}, trigger);
  if (!result?.response.ok) {
    elements.configuration.textContent = 'TransactKit Lite API is unavailable at this base URL.';
    elements.configuration.className = 'notice error';
    elements.connection.className = 'connection-status offline';
    elements.connection.innerHTML = '<i aria-hidden="true"></i> API unavailable';
    return;
  }
  const {stripeApiConfigured, webhookConfigured} = result.body;
  elements.configuration.textContent = `${stripeApiConfigured ? 'Stripe API configured' : 'Stripe API not configured'}; ${webhookConfigured ? 'webhook configured' : 'webhook not configured'}. No secret values are exposed.`;
  elements.configuration.className = `notice ${stripeApiConfigured ? 'ready' : 'warning'}`;
  elements.connection.className = 'connection-status';
  elements.connection.innerHTML = '<i aria-hidden="true"></i> API connected';
}

document.querySelector('#customer-create-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = formData(event.currentTarget);
  data.name = nullable(data.name);
  data.description = nullable(data.description);
  data.metadata = {source: 'api-test-console'};
  const result = await request('/api/customers', jsonPost(data), event.submitter);
  if (result?.response.ok) {
    setState('customerId', result.body.id);
    syncInputs();
  }
});

document.querySelector('#customer-get-form').addEventListener('submit', event => {
  event.preventDefault();
  const id = formData(event.currentTarget).id;
  request(`/api/customers/${encodeURIComponent(id)}`, {}, event.submitter);
});

document.querySelector('#checkout-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = formData(event.currentTarget);
  data.amount = Number(data.amount);
  data.customerEmail = nullable(data.customerEmail);
  data.stripeCustomerId = nullable(data.stripeCustomerId);
  data.metadata = {source: 'api-test-console'};
  const result = await request('/api/checkout/sessions', jsonPost(data), event.submitter);
  if (result?.response.ok) {
    setState('paymentId', result.body.paymentId);
    setState('checkoutSessionId', result.body.checkoutSessionId);
    elements.checkoutLink.href = result.body.checkoutUrl;
    elements.checkoutLink.hidden = !result.body.checkoutUrl;
    syncInputs();
  } else {
    elements.checkoutLink.hidden = true;
  }
});

document.querySelector('#payment-form').addEventListener('submit', event => {
  event.preventDefault();
  const id = formData(event.currentTarget).id;
  request(`/api/payments/${encodeURIComponent(id)}`, {}, event.submitter);
});

document.querySelector('#refund-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = formData(event.currentTarget);
  const paymentId = data.paymentId;
  delete data.paymentId;
  data.amount = data.amount ? Number(data.amount) : null;
  data.reason = nullable(data.reason);
  data.metadata = {source: 'api-test-console'};
  const result = await request(`/api/payments/${encodeURIComponent(paymentId)}/refund`, jsonPost(data), event.submitter);
  if (result?.response.ok) {
    setState('refundId', result.body.id);
    syncInputs();
  }
});

document.querySelector('#refund-get-form').addEventListener('submit', event => {
  event.preventDefault();
  const id = formData(event.currentTarget).id;
  request(`/api/refunds/${encodeURIComponent(id)}`, {}, event.submitter);
});

document.querySelector('[data-action="configuration"]').addEventListener('click', event => checkConfiguration(event.currentTarget));

document.querySelector('[data-action="clear-results"]').addEventListener('click', () => {
  elements.results.innerHTML = '<li class="empty-result"><span class="terminal-mark" aria-hidden="true">&gt;_</span><strong>No requests yet</strong><p>Choose an endpoint to inspect status, timing, and response data.</p></li>';
  setRunner('Ready');
});

document.querySelector('[data-action="reset"]').addEventListener('click', () => {
  document.querySelectorAll('form').forEach(form => form.reset());
  Object.keys(state).forEach(key => setState(key, ''));
  elements.checkoutLink.hidden = true;
  newReferences();
  document.querySelector('[data-action="clear-results"]').click();
  checkConfiguration();
});

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  const next = theme === 'dark' ? 'light' : 'dark';
  elements.themeToggle.setAttribute('aria-label', `Switch to ${next} theme`);
  localStorage.setItem('transactkit-theme', theme);
}

elements.themeToggle.addEventListener('click', () => applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'));
elements.baseUrl.value = window.location.protocol === 'file:' ? 'http://localhost:8080' : window.location.origin;
applyTheme(localStorage.getItem('transactkit-theme') || 'dark');
newReferences();
checkConfiguration();

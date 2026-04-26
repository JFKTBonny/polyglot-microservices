// ── Configuration ──────────────────────────────────────────────
const API = {
  base:      '',   // empty = same host (goes through Nginx gateway)
  users:     '/api/users',
  orders:    '/api/orders',
  products:  '/api/products',
  payments:  '/api/payments',
  analytics: '/api/analytics',
};

// ── State ──────────────────────────────────────────────────────
let currentPage = 'dashboard';
let products    = [];
let users       = [];

// ── Fetch helper ───────────────────────────────────────────────
async function api(path, options = {}) {
  try {
    const res = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.detail || data.error || 'Request failed');
    return data;
  } catch (err) {
    showToast(err.message, 'error');
    throw err;
  }
}

// ── Toast ──────────────────────────────────────────────────────
function showToast(msg, type = 'info') {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className   = `toast ${type} show`;
  setTimeout(() => t.className = 'toast', 3000);
}

// ── Modal ──────────────────────────────────────────────────────
function openModal(title, bodyHtml) {
  document.getElementById('modal-title').textContent = title;
  document.getElementById('modal-body').innerHTML    = bodyHtml;
  document.getElementById('modal-overlay').classList.add('open');
}

function closeModal() {
  document.getElementById('modal-overlay').classList.remove('open');
}

// ── Navigation ─────────────────────────────────────────────────
function navigate(page) {
  currentPage = page;
  document.querySelectorAll('.nav-item').forEach(item => {
    item.classList.toggle('active', item.dataset.page === page);
  });
  document.getElementById('page-title').textContent =
    page.charAt(0).toUpperCase() + page.slice(1);
  renderPage(page);
}

function refreshPage() { renderPage(currentPage); }

async function renderPage(page) {
  const content = document.getElementById('content');
  content.innerHTML = `<div class="loading"><div class="spinner"></div>Loading...</div>`;
  switch (page) {
    case 'dashboard': await renderDashboard(); break;
    case 'products':  await renderProducts();  break;
    case 'orders':    await renderOrders();    break;
    case 'payments':  await renderPayments();  break;
    case 'users':     await renderUsers();     break;
  }
}

// ── Health checks ──────────────────────────────────────────────
async function checkHealth() {
  const services = [
    { name: 'Users',     url: '/api/users' },
    { name: 'Orders',    url: '/api/orders' },
    { name: 'Products',  url: '/api/products' },
    { name: 'Payments',  url: '/api/payments' },
    { name: 'Analytics', url: '/api/analytics/summary' },
  ];

  const list = document.getElementById('health-list');
  list.innerHTML = '';

  for (const svc of services) {
    try {
      await fetch(svc.url);
      list.innerHTML += `
        <div class="status-item">
          <span>${svc.name}</span>
          <div class="status-dot up" title="Up"></div>
        </div>`;
    } catch {
      list.innerHTML += `
        <div class="status-item">
          <span>${svc.name}</span>
          <div class="status-dot down" title="Down"></div>
        </div>`;
    }
  }
}

// ── Dashboard ──────────────────────────────────────────────────
async function renderDashboard() {
  try {
    const [summary, daily, topProducts] = await Promise.all([
      api(API.analytics + '/summary'),
      api(API.analytics + '/daily?days=7'),
      api(API.analytics + '/top-products?limit=5'),
    ]);

    const totalOrders    = summary.total_orders    ?? 0;
    const totalRevenue   = summary.total_revenue   ?? 0;
    const totalPayments  = summary.total_payments  ?? 0;
    const failedPayments = summary.failed_payments ?? 0;
    const successRate    = summary.payment_success_rate ?? 0;

    const rateCardClass = successRate >= 80 ? 'card-accent-green'
                        : successRate >= 50 ? 'card-accent-yellow'
                        : 'card-accent-red';

    document.getElementById('content').innerHTML = `
      <div class="section-title">Overview</div>
      <div class="cards-grid">
        <div class="card card-accent-blue">
          <div class="card-label">Total Orders</div>
          <div class="card-value">${totalOrders}</div>
          <div class="card-sub">All time</div>
        </div>
        <div class="card card-accent-green">
          <div class="card-label">Total Revenue</div>
          <div class="card-value">$${parseFloat(totalRevenue).toFixed(2)}</div>
          <div class="card-sub">Completed payments</div>
        </div>
        <div class="card card-accent-yellow">
          <div class="card-label">Total Payments</div>
          <div class="card-value">${totalPayments}</div>
          <div class="card-sub">${failedPayments} failed</div>
        </div>
        <div class="card ${rateCardClass}">
          <div class="card-label">Success Rate</div>
          <div class="card-value">${successRate}%</div>
          <div class="card-sub">Payment success</div>
        </div>
      </div>

      <div class="section-title">Daily Metrics</div>
      <div class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Orders</th>
              <th>Revenue</th>
              <th>Payments</th>
              <th>Failed</th>
              <th>Items Reserved</th>
            </tr>
          </thead>
          <tbody>
            ${daily && daily.length ? daily.map(d => `
              <tr>
                <td>${d.date}</td>
                <td>${d.total_orders ?? 0}</td>
                <td>$${parseFloat(d.total_revenue ?? 0).toFixed(2)}</td>
                <td>${d.successful_payments ?? 0}</td>
                <td>${d.failed_payments ?? 0}</td>
                <td>${d.items_reserved ?? 0}</td>
              </tr>`).join('') : `
              <tr><td colspan="6">
                <div class="empty">
                  <div class="empty-icon">📊</div>
                  <p>No daily metrics yet — create some orders</p>
                </div>
              </td></tr>`}
          </tbody>
        </table>
      </div>

      <div class="section-title">Top Products</div>
      <div class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Product</th>
              <th>SKU</th>
              <th>Total Ordered</th>
            </tr>
          </thead>
          <tbody>
            ${topProducts && topProducts.length ? topProducts.map((p, i) => `
              <tr>
                <td>${i + 1}</td>
                <td>${p.name ?? 'Unknown'}</td>
                <td><span class="badge badge-blue">${p.product_id ?? '-'}</span></td>
                <td>${p.total_ordered ?? 0}</td>
              </tr>`).join('') : `
              <tr><td colspan="4">
                <div class="empty">
                  <div class="empty-icon">📦</div>
                  <p>No orders yet</p>
                </div>
              </td></tr>`}
          </tbody>
        </table>
      </div>
    `;

  } catch (err) {
    document.getElementById('content').innerHTML = `
      <div class="empty">
        <div class="empty-icon">📊</div>
        <p>No analytics data yet — create some orders first</p>
        <br>
        <button class="btn btn-primary" onclick="navigate('orders')">
          Create an Order
        </button>
      </div>
    `;
  }
}


// ── Products ───────────────────────────────────────────────────
async function renderProducts() {
  const data = await api(API.products);
  products   = data.data || [];

  document.getElementById('content').innerHTML = `
    <div class="table-wrapper">
      <div class="table-header">
        <span class="table-title">Inventory (${products.length} products)</span>
        <button class="btn btn-primary" onclick="showAddProductModal()">+ Add Product</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>SKU</th><th>Name</th><th>Price</th>
            <th>Stock</th><th>Reserved</th><th>Available</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          ${products.map(p => `
            <tr>
              <td><span class="badge badge-blue">${p.sku}</span></td>
              <td>${p.name}</td>
              <td>$${p.price}</td>
              <td>${p.stock_quantity}</td>
              <td>${p.reserved_quantity}</td>
              <td>
                <span class="badge ${p.available_quantity > 10 ? 'badge-green' :
                  p.available_quantity > 0 ? 'badge-yellow' : 'badge-red'}">
                  ${p.available_quantity}
                </span>
              </td>
              <td>
                <button class="btn btn-outline"
                  onclick="showUpdateStockModal('${p.id}', '${p.name}')">
                  Update Stock
                </button>
              </td>
            </tr>`).join('')}
        </tbody>
      </table>
    </div>
  `;
}

function showAddProductModal() {
  openModal('Add Product', `
    <div class="form-group">
      <label>SKU</label>
      <input id="p-sku" placeholder="SKU-007">
    </div>
    <div class="form-group">
      <label>Name</label>
      <input id="p-name" placeholder="Product name">
    </div>
    <div class="form-group">
      <label>Price</label>
      <input id="p-price" type="number" placeholder="0.00">
    </div>
    <div class="form-group">
      <label>Initial Stock</label>
      <input id="p-stock" type="number" placeholder="100">
    </div>
    <button class="btn btn-primary" style="width:100%" onclick="addProduct()">
      Add Product
    </button>
  `);
}

async function addProduct() {
  const body = {
    sku:           document.getElementById('p-sku').value,
    name:          document.getElementById('p-name').value,
    price:         parseFloat(document.getElementById('p-price').value),
    stock_quantity: parseInt(document.getElementById('p-stock').value),
  };
  await api(API.products, { method: 'POST', body: JSON.stringify(body) });
  showToast('Product added', 'success');
  closeModal();
  renderProducts();
}

function showUpdateStockModal(id, name) {
  openModal(`Update Stock — ${name}`, `
    <div class="form-group">
      <label>Action</label>
      <select id="s-action">
        <option value="add">Add stock</option>
        <option value="subtract">Subtract stock</option>
        <option value="set">Set stock</option>
      </select>
    </div>
    <div class="form-group">
      <label>Quantity</label>
      <input id="s-qty" type="number" value="10">
    </div>
    <button class="btn btn-primary" style="width:100%"
      onclick="updateStock('${id}')">Update</button>
  `);
}

async function updateStock(id) {
  const body = {
    action:   document.getElementById('s-action').value,
    quantity: parseInt(document.getElementById('s-qty').value),
  };
  await api(`${API.products}/${id}/stock`, { method: 'PUT', body: JSON.stringify(body) });
  showToast('Stock updated', 'success');
  closeModal();
  renderProducts();
}

// ── Orders ─────────────────────────────────────────────────────
async function renderOrders() {
  const [ordersData, usersData] = await Promise.all([
    api(API.orders),
    api(API.users),
  ]);

  users = usersData.data || [];
  const orders = ordersData.data || [];

  document.getElementById('content').innerHTML = `
    <div class="table-wrapper">
      <div class="table-header">
        <span class="table-title">Orders (${orders.length})</span>
        <button class="btn btn-primary" onclick="showCreateOrderModal()">
          + Create Order
        </button>
      </div>
      <table>
        <thead>
          <tr>
            <th>Order ID</th><th>User</th><th>Total</th>
            <th>Status</th><th>Items</th><th>Created</th>
          </tr>
        </thead>
        <tbody>
          ${orders.length ? orders.map(o => `
            <tr>
              <td style="font-family:monospace;font-size:0.75rem">
                ${o.id.substring(0, 8)}...
              </td>
              <td>${o.user_id.substring(0, 8)}...</td>
              <td>$${o.total}</td>
              <td>
                <span class="badge ${o.status === 'pending' ? 'badge-yellow' :
                  o.status === 'completed' ? 'badge-green' : 'badge-red'}">
                  ${o.status}
                </span>
              </td>
              <td>${o.items.length} item(s)</td>
              <td>${new Date(o.created_at).toLocaleString()}</td>
            </tr>`).join('') : `
            <tr><td colspan="6">
              <div class="empty">
                <div class="empty-icon">🛒</div>
                <p>No orders yet</p>
              </div>
            </td></tr>`}
        </tbody>
      </table>
    </div>
  `;
}

async function showCreateOrderModal() {
  // Refresh products and users lists
  const [pd, ud] = await Promise.all([api(API.products), api(API.users)]);
  products = pd.data || [];
  users    = ud.data || [];

  openModal('Create Order', `
    <div class="form-group">
      <label>User</label>
      <select id="o-user">
        ${users.length
          ? users.map(u => `<option value="${u.id}">${u.name} (${u.email})</option>`).join('')
          : '<option>No users — create one first</option>'}
      </select>
    </div>
    <div class="form-group">
      <label>Product</label>
      <select id="o-product">
        ${products.map(p =>
          `<option value="${p.sku}" data-price="${p.price}">
            ${p.name} — $${p.price} (${p.available_quantity} available)
          </option>`).join('')}
      </select>
    </div>
    <div class="form-group">
      <label>Quantity</label>
      <input id="o-qty" type="number" value="1" min="1">
    </div>
    <button class="btn btn-primary" style="width:100%" onclick="createOrder()">
      Place Order
    </button>
  `);
}

async function createOrder() {
  const userId    = document.getElementById('o-user').value;
  const productEl = document.getElementById('o-product');
  const sku       = productEl.value;
  const price     = parseFloat(productEl.selectedOptions[0].dataset.price);
  const qty       = parseInt(document.getElementById('o-qty').value);
  const name      = productEl.selectedOptions[0].text.split(' — ')[0];

  const body = {
    user_id: userId,
    items: [{ product_id: sku, name, quantity: qty, price }],
  };

  await api(API.orders, { method: 'POST', body: JSON.stringify(body) });
  showToast('Order created — processing...', 'success');
  closeModal();
  renderOrders();
}

// ── Payments ───────────────────────────────────────────────────
async function renderPayments() {
  const payments = await api(API.payments);

  document.getElementById('content').innerHTML = `
    <div class="table-wrapper">
      <div class="table-header">
        <span class="table-title">Payments (${payments.length})</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>Payment ID</th><th>Order ID</th><th>Amount</th>
            <th>Status</th><th>Method</th><th>Created</th>
          </tr>
        </thead>
        <tbody>
          ${payments.length ? payments.map(p => `
            <tr>
              <td style="font-family:monospace;font-size:0.75rem">
                ${p.id.substring(0, 8)}...
              </td>
              <td style="font-family:monospace;font-size:0.75rem">
                ${p.orderId.substring(0, 8)}...
              </td>
              <td>$${p.amount}</td>
              <td>
                <span class="badge ${p.status === 'completed' ? 'badge-green' :
                  p.status === 'failed' ? 'badge-red' : 'badge-yellow'}">
                  ${p.status}
                </span>
              </td>
              <td>${p.paymentMethod}</td>
              <td>${new Date(p.createdAt).toLocaleString()}</td>
            </tr>`).join('') : `
            <tr><td colspan="6">
              <div class="empty">
                <div class="empty-icon">💳</div>
                <p>No payments yet</p>
              </div>
            </td></tr>`}
        </tbody>
      </table>
    </div>
  `;
}

// ── Users ──────────────────────────────────────────────────────
async function renderUsers() {
  const data  = await api(API.users);
  const users = data.data || [];

  document.getElementById('content').innerHTML = `
    <div class="table-wrapper">
      <div class="table-header">
        <span class="table-title">Users (${users.length})</span>
        <button class="btn btn-primary" onclick="showCreateUserModal()">
          + Create User
        </button>
      </div>
      <table>
        <thead>
          <tr><th>ID</th><th>Name</th><th>Email</th><th>Created</th></tr>
        </thead>
        <tbody>
          ${users.length ? users.map(u => `
            <tr>
              <td style="font-family:monospace;font-size:0.75rem">
                ${u.id.substring(0, 8)}...
              </td>
              <td>${u.name}</td>
              <td>${u.email}</td>
              <td>${new Date(u.created_at).toLocaleString()}</td>
            </tr>`).join('') : `
            <tr><td colspan="4">
              <div class="empty">
                <div class="empty-icon">👤</div>
                <p>No users yet</p>
              </div>
            </td></tr>`}
        </tbody>
      </table>
    </div>
  `;
}

function showCreateUserModal() {
  openModal('Create User', `
    <div class="form-group">
      <label>Name</label>
      <input id="u-name" placeholder="Alice">
    </div>
    <div class="form-group">
      <label>Email</label>
      <input id="u-email" type="email" placeholder="alice@example.com">
    </div>
    <button class="btn btn-primary" style="width:100%" onclick="createUser()">
      Create User
    </button>
  `);
}

async function createUser() {
  const body = {
    name:  document.getElementById('u-name').value,
    email: document.getElementById('u-email').value,
  };
  await api(API.users, { method: 'POST', body: JSON.stringify(body) });
  showToast('User created', 'success');
  closeModal();
  renderUsers();
}

// ── Event listeners ────────────────────────────────────────────
document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', e => {
    e.preventDefault();
    navigate(item.dataset.page);
  });
});


// ── Token management ───────────────────────────────────────────
let authToken = null;

function getToken() { return authToken; }
function setToken(token) { authToken = token; }
function clearToken() { authToken = null; }
function isLoggedIn() { return !!authToken; }

// ── Updated api() function ─────────────────────────────────────
async function api(path, options = {}) {
  try {
    const headers = { 'Content-Type': 'application/json' };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

    const res = await fetch(path, { headers, ...options });

    if (res.status === 401) {
      clearToken();
      showLogin();
      return;
    }

    const data = await res.json();
    if (!res.ok) throw new Error(data.detail || data.error || 'Request failed');
    return data;
  } catch (err) {
    showToast(err.message, 'error');
    throw err;
  }
}


// ── Login page ─────────────────────────────────────────────────
function showLogin() {
  document.getElementById('content').innerHTML = `
    <div style="max-width:400px;margin:80px auto;">
      <div class="card">
        <div class="card-label" style="font-size:1.2rem;margin-bottom:20px">
          ⬡ Polyglot Microservices
        </div>
        <div class="form-group">
          <label>Email</label>
          <input id="login-email" type="email" placeholder="alice@example.com">
        </div>
        <div class="form-group">
          <label>Password</label>
          <input id="login-password" type="password" placeholder="••••••••">
        </div>
        <button class="btn btn-primary" style="width:100%;margin-bottom:10px"
          onclick="doLogin()">Login</button>
        <button class="btn btn-outline" style="width:100%"
          onclick="showRegister()">Register</button>
      </div>
    </div>
  `;
}

function showRegister() {
  document.getElementById('content').innerHTML = `
    <div style="max-width:400px;margin:80px auto;">
      <div class="card">
        <div class="card-label" style="font-size:1.2rem;margin-bottom:20px">
          ⬡ Create Account
        </div>
        <div class="form-group">
          <label>Name</label>
          <input id="reg-name" placeholder="Alice">
        </div>
        <div class="form-group">
          <label>Email</label>
          <input id="reg-email" type="email" placeholder="alice@example.com">
        </div>
        <div class="form-group">
          <label>Password</label>
          <input id="reg-password" type="password" placeholder="Min 8 chars, 1 uppercase, 1 number">
        </div>
        <button class="btn btn-primary" style="width:100%;margin-bottom:10px"
          onclick="doRegister()">Register</button>
        <button class="btn btn-outline" style="width:100%"
          onclick="showLogin()">Back to Login</button>
      </div>
    </div>
  `;
}

async function doLogin() {
  const email    = document.getElementById('login-email').value;
  const password = document.getElementById('login-password').value;
  try {
    const data = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    }).then(r => r.json());

    if (data.access_token) {
      setToken(data.access_token);
      showToast('Welcome back!', 'success');
      navigate('dashboard');
    } else {
      showToast(data.detail || 'Login failed', 'error');
    }
  } catch (err) {
    showToast('Login failed', 'error');
  }
}

async function doRegister() {
  const name     = document.getElementById('reg-name').value;
  const email    = document.getElementById('reg-email').value;
  const password = document.getElementById('reg-password').value;
  try {
    const data = await fetch('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, email, password, role: 'user' })
    }).then(r => r.json());

    if (data.tokens) {
      setToken(data.tokens.access_token);
      showToast('Account created!', 'success');
      navigate('dashboard');
    } else {
      showToast(data.detail || 'Registration failed', 'error');
    }
  } catch (err) {
    showToast('Registration failed', 'error');
  }
}

// ── Init ───────────────────────────────────────────────────────
checkHealth();

// Show login if no token
if (!isLoggedIn()) {
  showLogin();
} else {
  navigate('dashboard');
}

setInterval(checkHealth, 30000);
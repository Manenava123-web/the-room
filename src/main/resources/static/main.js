/* ═══════════════════════════════════════════
   CONFIG
═══════════════════════════════════════════ */
const BASE_URL    = '/api/v1';
const SERVER_BASE = '';
const imgSrc = path => path ? `${SERVER_BASE}${path}?ngrok-skip-browser-warning=1` : null;

/* ═══════════════════════════════════════════
   SESSION
═══════════════════════════════════════════ */
const getToken    = () => localStorage.getItem('tr_token');
const getSavedUser = () => { const u = localStorage.getItem('tr_user'); return u ? JSON.parse(u) : null; };

function saveSession(data) {
  localStorage.setItem('tr_token', data.token);
  localStorage.setItem('tr_user', JSON.stringify({
    id:    data.id,
    name:  `${data.nombre} ${data.apellido}`,
    email: data.email,
    rol:   data.rol
  }));
}

function clearSession() {
  localStorage.removeItem('tr_token');
  localStorage.removeItem('tr_user');
}

/* ═══════════════════════════════════════════
   API HELPER
═══════════════════════════════════════════ */
async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json', 'ngrok-skip-browser-warning': '1' };
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });
  if (res.status === 204) return null;
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Error en el servidor');
  return data;
}

/* ═══════════════════════════════════════════
   AGENDA — CONSTANTES
═══════════════════════════════════════════ */
const DAYS        = ['Lun','Mar','Mié','Jue','Vie'];          // solo Lun–Vie
const MONTH_NAMES = ['enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre'];
const DIA_IDX     = { LUNES:0, MARTES:1, MIERCOLES:2, JUEVES:3, VIERNES:4 }; // sábado/domingo excluidos
const DIAS_ORDEN  = ['LUNES','MARTES','MIERCOLES','JUEVES','VIERNES'];

let weekOffset  = 0;
let agendaFilter = 'todos';

function getMonday(offset) {
  const today = new Date();
  today.setHours(0,0,0,0);
  const day    = today.getDay() === 0 ? 6 : today.getDay() - 1;
  const monday = new Date(today);
  monday.setDate(today.getDate() - day + offset * 7);
  return monday;
}

/* ═══════════════════════════════════════════
   AGENDA — BUILD (async)
═══════════════════════════════════════════ */
function revelarContenido(disc) {
  document.querySelectorAll('.contenido-disciplina').forEach(el => el.classList.remove('contenido-disciplina'));
  const esCycling = !disc || disc === 'spinning';
  document.getElementById('navDiscCycling').classList.toggle('nav-disc-active', esCycling);
  document.getElementById('navDiscPilates').classList.toggle('nav-disc-active', !esCycling);
  document.getElementById('drawerDiscCycling').classList.toggle('nav-disc-active', esCycling);
  document.getElementById('drawerDiscPilates').classList.toggle('nav-disc-active', !esCycling);
  document.getElementById('horarios-cycling').style.display    = esCycling ? '' : 'none';
  document.getElementById('horarios-pilates').style.display    = esCycling ? 'none' : '';
  const cardVisible = document.getElementById(esCycling ? 'clase-card-cycling' : 'clase-card-pilates');
  const cardHidden  = document.getElementById(esCycling ? 'clase-card-pilates' : 'clase-card-cycling');
  cardVisible.style.display = '';
  cardVisible.style.width   = '70%';
  cardHidden.style.display  = 'none';
  cardHidden.style.width    = '';
  const grid = document.querySelector('.clases-grid');
  grid.style.display        = 'flex';
  grid.style.justifyContent = 'center';
  document.getElementById('pkg-cycling').style.display = esCycling ? '' : 'none';
  document.getElementById('pkg-pilates').style.display = esCycling ? 'none' : '';
  document.querySelector('.hero-cta').classList.add('visible');
}

async function buildAgenda(filter) {
  if (filter !== undefined) agendaFilter = filter;
  const g = document.getElementById('agendaGrid');
  g.innerHTML = '<div class="agenda-loading">Cargando clases...</div>';

  let clases;
  try {
    clases = await api('GET', `/clases/semana?offset=${weekOffset}`);
  } catch(e) {
    g.innerHTML = '<div class="agenda-loading">No se pudieron cargar las clases.</div>';
    return;
  }

  g.innerHTML = '';
  const monday   = getMonday(weekOffset);
  const today    = new Date(); today.setHours(0,0,0,0);
  const todayMs  = today.getTime();
  const nowHour  = new Date().getHours();

  document.getElementById('btnPrevWeek').disabled = weekOffset <= 0;

  const viernes = new Date(monday); viernes.setDate(monday.getDate() + 4);
  const fmt = d => `${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`;
  const yearLabel = viernes.getFullYear() !== monday.getFullYear() ? ` ${viernes.getFullYear()}` : '';
  document.getElementById('weekLabel').textContent =
    `${fmt(monday)} — ${fmt(viernes)}${yearLabel} ${viernes.getFullYear()}`;

  // Solo clases Lun–Vie
  const clasesSemana = clases.filter(c => c.diaSemana in DIA_IDX);
  const horas = [...new Set(clasesSemana.map(c => c.hora))].sort();

  // Esquina vacía
  const corner = document.createElement('div');
  corner.className = 'aw-corner';
  g.appendChild(corner);

  // Encabezados Lun–Vie con fecha
  for (let d = 0; d < 5; d++) {
    const date    = new Date(monday); date.setDate(monday.getDate() + d);
    const isToday = date.getTime() === today.getTime();
    const el      = document.createElement('div');
    el.className  = 'aw-day' + (isToday ? ' today' : '');
    el.innerHTML  = `<span class="aw-day-name">${DAYS[d]}</span><span class="aw-day-num">${date.getDate()}</span>`;
    g.appendChild(el);
  }

  // Fechas de cada columna precalculadas
  const colDates = Array.from({length:5}, (_, d) => {
    const dt = new Date(monday); dt.setDate(monday.getDate() + d); return dt;
  });

  // Filas por hora
  horas.forEach(hora => {
    const timeEl      = document.createElement('div');
    timeEl.className  = 'aw-time';
    timeEl.textContent = hora.replace(/^0/, '');
    g.appendChild(timeEl);

    for (let d = 0; d < 5; d++) {
      const cell       = document.createElement('div');
      const colMs      = colDates[d].getTime();
      const isDatePast = colMs < todayMs;
      const isToday    = colMs === todayMs;
      cell.className   = 'aw-cell';
      const cls = clasesSemana.find(c => c.hora === hora && DIA_IDX[c.diaSemana] === d);

      if (cls && (agendaFilter === 'todos' ||
         (agendaFilter === 'spinning' && cls.tipo === 'SPINNING') ||
         (agendaFilter === 'pilates'  && cls.tipo === 'PILATES'))) {

        const classHour = parseInt(cls.hora.split(':')[0]);
        const isPast    = isDatePast || (isToday && classHour < nowHour);
        const full = cls.llena || isPast;
        const low  = !isPast && !cls.llena && cls.lugaresDisponibles <= 2;
        const pill = document.createElement('a');
        pill.className = 'class-pill ' + (isPast ? 'pill-past' : full ? 'pill-full' : cls.tipo === 'SPINNING' ? 'pill-spin' : 'pill-pilates');
        pill.innerHTML = `
          <span class="pill-name">${cls.tipo === 'SPINNING' ? 'Indoor Cycling' : 'Pilates'}</span>
          <span class="pill-instructor">${cls.instructor ?? ''}</span>
          <span class="pill-spots${low ? ' low' : ''}">${isPast ? 'PASADO' : cls.llena ? 'LLENO' : `${cls.lugaresDisponibles} lugares`}</span>`;

        if (!isPast && !cls.llena) {
          const clsSnap = { ...cls };
          const dayIdx  = d;
          pill.style.cursor = 'pointer';
          pill.onclick = () => {
            if (!currentUser) { openAuth('login'); return; }
            abrirSeatSelector(clsSnap, dayIdx);
          };
        }
        cell.appendChild(pill);
      }
      g.appendChild(cell);
    }
  });
}

/* ═══════════════════════════════════════════
   AGENDA — CONFIRMAR RESERVACIÓN
═══════════════════════════════════════════ */
let pendingReservacion = null;

const isAdmin = () => currentUser?.rol === 'ADMIN';

/* ═══════════════════════════════════════════
   SELECCIÓN DE LUGAR
═══════════════════════════════════════════ */
let selectedSeat    = null;
let pendingClsSeat  = null;
let pendingDaySeat  = null;

async function abrirSeatSelector(cls, dayIdx) {
  selectedSeat   = null;
  pendingClsSeat = cls;
  pendingDaySeat = dayIdx;

  const monday   = getMonday(weekOffset);
  const fechaDt  = new Date(monday);
  fechaDt.setDate(monday.getDate() + dayIdx);
  const fechaStr = `${fechaDt.getFullYear()}-${String(fechaDt.getMonth()+1).padStart(2,'0')}-${String(fechaDt.getDate()).padStart(2,'0')}`;

  const isSpin = cls.tipo === 'SPINNING';
  document.getElementById('seatTitle').textContent =
    isSpin ? 'Elige tu bicicleta' : 'Elige tu lugar en el reformer';
  document.getElementById('seatSub').textContent =
    `${isSpin ? 'Indoor Cycling' : 'Pilates'} · ${cls.hora.replace(/^0/,'')} · ${formatDate(fechaStr)}`;
  document.getElementById('seatContinueBtn').disabled = true;
  document.getElementById('seatMapContainer').innerHTML =
    '<div class="agenda-loading">Cargando lugares...</div>';
  document.getElementById('seatOverlay').classList.add('show');

  try {
    const data = await api('GET', `/clases/${cls.id}/lugares?fecha=${fechaStr}`);
    renderSeatMap(cls.tipo, data.cupoTotal, data.ocupados);
  } catch(e) {
    document.getElementById('seatMapContainer').innerHTML =
      `<p class="resv-empty">${e.message}</p>`;
  }
}

function renderSeatMap(tipo, cupoTotal, ocupados) {
  const isSpin = tipo === 'SPINNING';
  document.getElementById('seatMapContainer').innerHTML =
    isSpin ? renderCyclingMap(cupoTotal, ocupados) : renderPilatesMap(cupoTotal, ocupados);
}

function renderCyclingMap(cupoTotal, ocupados) {
  let html = `
    <div class="seat-map">
      <div class="seat-stage">
        <div class="seat-stage-label">Instructor · Pantalla</div>
      </div>`;
  let seat = 1, row = 0;
  while (seat <= cupoTotal) {
    const rowMax   = 4;
    const rowSeats = [];
    for (let i = 0; i < rowMax && seat <= cupoTotal; i++) rowSeats.push(seat++);
    const stagger  = row % 2 === 1 ? ' staggered' : '';
    html += `<div class="seat-row${stagger}">`;
    rowSeats.forEach(n => {
      const ocu = ocupados.includes(n);
      html += `<button class="seat-bike ${ocu ? 'ocupado' : 'disponible'}"
        ${ocu ? 'disabled' : `onclick="selectSeat(${n})"`} id="seat-${n}">
        <svg class="seat-bike-svg" viewBox="0 0 24 24"><path d="M5 20.5A3.5 3.5 0 0 1 1.5 17 3.5 3.5 0 0 1 5 13.5 3.5 3.5 0 0 1 8.5 17 3.5 3.5 0 0 1 5 20.5M5 12A5 5 0 0 0 0 17a5 5 0 0 0 5 5 5 5 0 0 0 5-5 5 5 0 0 0-5-5m9.8-2H19V8h-3.2L13 5.5c-.4-.5-1-.8-1.6-.8-.8 0-1.5.5-1.8 1.2L7.4 11c-.2.5-.4 1-.4 1.5A2.5 2.5 0 0 0 9.5 15H11v5h2v-7H9.5c-.1 0-.2 0-.2-.1l2-4.5L13 11h1.8M19 20.5A3.5 3.5 0 0 1 15.5 17 3.5 3.5 0 0 1 19 13.5 3.5 3.5 0 0 1 22.5 17 3.5 3.5 0 0 1 19 20.5m0-8.5a5 5 0 0 0-5 5 5 5 0 0 0 5 5 5 5 0 0 0 5-5 5 5 0 0 0-5-5m-2-4.5a1.5 1.5 0 0 0 1.5 1.5A1.5 1.5 0 0 0 20 7.5 1.5 1.5 0 0 0 18.5 6 1.5 1.5 0 0 0 17 7.5z"/></svg>
        <span class="seat-num">${n}</span>
      </button>`;
    });
    html += '</div>';
    row++;
  }
  return html + '</div>';
}

function renderPilatesMap(cupoTotal, ocupados) {
  let html = `
    <div class="seat-map">
      <div class="seat-stage">
        <div class="seat-stage-label">Instructor · Espejo</div>
      </div>`;
  let seat = 1;
  while (seat <= cupoTotal) {
    html += `<div class="seat-row">`;
    for (let i = 0; i < 3 && seat <= cupoTotal; i++) {
      const n = seat++, ocu = ocupados.includes(n);
      html += `<button class="seat-mat ${ocu ? 'ocupado' : 'disponible'}"
        ${ocu ? 'disabled' : `onclick="selectSeat(${n})"`} id="seat-${n}">
        <div class="seat-mat-rails">
          <div class="seat-mat-rail"></div>
          <div class="seat-mat-rail"></div>
          <div class="seat-mat-rail"></div>
        </div>
        <span class="seat-num">Mat ${n}</span>
      </button>`;
    }
    html += '</div>';
  }
  return html + '</div>';
}

function selectSeat(num) {
  if (selectedSeat) {
    const prev = document.getElementById(`seat-${selectedSeat}`);
    if (prev) { prev.classList.remove('seleccionado'); prev.classList.add('disponible'); }
  }
  selectedSeat = num;
  const el = document.getElementById(`seat-${num}`);
  if (el) { el.classList.remove('disponible'); el.classList.add('seleccionado'); }
  document.getElementById('seatContinueBtn').disabled = false;
}

function confirmarSeat() {
  if (!selectedSeat || !pendingClsSeat) return;
  const seat = selectedSeat;
  const cls  = pendingClsSeat;
  const day  = pendingDaySeat;
  closeSeatSelector();
  abrirConfirmacion(cls, day, seat);
}

function closeSeatSelector() {
  document.getElementById('seatOverlay').classList.remove('show');
  selectedSeat = null;
}
function seatOverlayClick(e) {
  if (e.target === document.getElementById('seatOverlay')) closeSeatSelector();
}

async function abrirConfirmacion(cls, dayIdx, lugarNumero = null) {
  const monday = getMonday(weekOffset);
  const fecha  = new Date(monday);
  fecha.setDate(monday.getDate() + dayIdx);
  const fechaStr = `${fecha.getFullYear()}-${String(fecha.getMonth()+1).padStart(2,'0')}-${String(fecha.getDate()).padStart(2,'0')}`;

  pendingReservacion = { claseId: cls.id, fecha: fechaStr, lugarNumero, tipo: cls.tipo };

  const isSpin = cls.tipo === 'SPINNING';
  document.getElementById('confirmCard').innerHTML = `
    <div class="confirm-row">
      <span class="confirm-label">Clase</span>
      <span class="confirm-tipo ${isSpin ? 'spin' : 'pilates'}">${isSpin ? 'Indoor Cycling' : 'Pilates'}</span>
    </div>
    <div class="confirm-row">
      <span class="confirm-label">Instructor</span>
      <span class="confirm-value">${cls.instructor}</span>
    </div>
    <div class="confirm-row">
      <span class="confirm-label">Fecha</span>
      <span class="confirm-value">${formatDate(fechaStr)}</span>
    </div>
    <div class="confirm-row">
      <span class="confirm-label">Horario</span>
      <span class="confirm-value">${cls.hora.replace(/^0/,'')}</span>
    </div>
    <div class="confirm-row">
      <span class="confirm-label">Lugares disponibles</span>
      <span class="confirm-value">${cls.lugaresDisponibles}</span>
    </div>
    ${lugarNumero ? `
    <div class="confirm-row">
      <span class="confirm-label">Tu lugar</span>
      <span class="confirm-value confirm-seat">${cls.tipo === 'SPINNING' ? '🚲' : '🧘'} #${lugarNumero}</span>
    </div>` : ''}`;

  // Si es admin → mostrar selector buscable de cliente
  const adminWrap = document.getElementById('adminClienteWrap');
  if (isAdmin()) {
    adminWrap.style.display = 'block';
    resetClienteSearch();
    const searchInput = document.getElementById('clienteSearch');
    searchInput.placeholder = 'Cargando clientes...';
    searchInput.disabled = true;
    try {
      const clientes = await api('GET', '/admin/usuarios');
      searchInput.disabled = false;
      searchInput.placeholder = 'Buscar por nombre o correo...';
      initClienteSearch(clientes);
    } catch(e) {
      searchInput.placeholder = 'Error al cargar clientes';
    }
  } else {
    adminWrap.style.display = 'none';
  }

  document.getElementById('confirmOverlay').classList.add('show');
}

async function doConfirmReservacion() {
  if (!pendingReservacion) return;

  let body, endpoint;
  if (isAdmin()) {
    const usuarioId = document.getElementById('adminClienteId').value;
    if (!usuarioId) { showToast('Selecciona un cliente', 'Debes elegir a quién asignar la reservación.'); return; }
    body     = { ...pendingReservacion, usuarioId: parseInt(usuarioId) };
    endpoint = '/admin/reservaciones';
  } else {
    body     = pendingReservacion;
    endpoint = '/reservaciones';
  }

  const btn = document.getElementById('confirmBtn');
  btn.disabled = true; btn.textContent = 'Reservando...';

  try {
    await api('POST', endpoint, body);
    closeConfirm();
    showToast('¡Lugar reservado!', 'La reservación ha sido confirmada.', 'success');
    buildAgenda();
    loadCreditos();
  } catch(e) {
    if (isAdmin()) {
      showToast('Sin créditos disponibles', e.message);
    } else if (e.message.includes('Sin clases de Indoor Cycling')) {
      closeConfirm();
      _showSinCreditos('cycling');
    } else if (e.message.includes('Sin clases de Pilates')) {
      closeConfirm();
      _showSinCreditos('pilates');
    } else {
      showToast('No fue posible reservar', e.message);
    }
  } finally {
    btn.disabled = false; btn.textContent = 'Confirmar lugar';
    pendingReservacion = null;
  }
}

/* ═══════════════════════════════════════════
   CONFIRMACIÓN GENÉRICA
═══════════════════════════════════════════ */
let _gcCallback = null;

function showConfirm(title, message, btnLabel, btnClass, onConfirm) {
  _gcCallback = onConfirm;
  document.getElementById('gcTitle').textContent   = title;
  document.getElementById('gcMessage').textContent = message;
  const btn = document.getElementById('gcConfirmBtn');
  btn.textContent = btnLabel || 'Confirmar';
  btn.className   = `btn-submit${btnClass ? ' ' + btnClass : ''}`;
  document.getElementById('genericConfirmOverlay').classList.add('show');
}

function execGenericConfirm() {
  const cb = _gcCallback;
  closeGenericConfirm();
  if (cb) cb();
}

function closeGenericConfirm() {
  document.getElementById('genericConfirmOverlay').classList.remove('show');
  _gcCallback = null;
}

function closeConfirm() {
  document.getElementById('confirmOverlay').classList.remove('show');
  pendingReservacion = null;
  resetClienteSearch();
}
function confirmOverlayClick(e) { if (e.target === document.getElementById('confirmOverlay')) closeConfirm(); }

/* ═══════════════════════════════════════════
   SEARCHABLE CLIENTE SELECT
═══════════════════════════════════════════ */
let clientesList = [];
let selectedClienteId = null;

function initClienteSearch(clientes) {
  clientesList = clientes;
  const input    = document.getElementById('clienteSearch');
  const dropdown = document.getElementById('ssDropdown');

  renderClienteOptions(clientes);

  input.addEventListener('input', () => {
    const q = input.value.trim().toLowerCase();
    selectedClienteId = null;
    document.getElementById('adminClienteId').value = '';
    const filtered = q
      ? clientesList.filter(c =>
          `${c.nombre} ${c.apellido}`.toLowerCase().includes(q) ||
          c.email.toLowerCase().includes(q))
      : clientesList;
    renderClienteOptions(filtered);
    dropdown.classList.add('open');
  });

  input.addEventListener('focus', () => {
    renderClienteOptions(clientesList);
    dropdown.classList.add('open');
  });

  document.addEventListener('click', closeClienteDropdown);
}

function renderClienteOptions(clientes) {
  const dropdown = document.getElementById('ssDropdown');
  if (!clientes.length) {
    dropdown.innerHTML = '<div class="ss-empty">Sin resultados</div>';
    return;
  }
  dropdown.innerHTML = clientes.map(c => `
    <div class="ss-option${selectedClienteId == c.id ? ' selected' : ''}"
         onclick="selectCliente(${c.id}, '${c.nombre} ${c.apellido}', '${c.email}')">
      <div class="ss-option-name">${c.nombre} ${c.apellido}</div>
      <div class="ss-option-email">${c.email}</div>
    </div>`).join('');
}

async function selectCliente(id, nombre, email) {
  selectedClienteId = id;
  document.getElementById('adminClienteId').value = id;
  document.getElementById('clienteSearch').value  = `${nombre} — ${email}`;
  document.getElementById('ssDropdown').classList.remove('open');

  const badge = document.getElementById('adminCreditosBadge');
  if (badge) {
    badge.textContent = '…';
    try {
      const data = await api('GET', `/admin/creditos/${id}`);
      const cyc = data.creditosCycling ?? 0, pil = data.creditosPilates ?? 0;
      const disc = pendingReservacion?.tipo === 'SPINNING' ? 'CYCLING' : 'PILATES';
      const discCredits = disc === 'CYCLING' ? cyc : pil;
      badge.innerHTML = `Cycling: <strong>${cyc}</strong> · Pilates: <strong>${pil}</strong>`;
      if (discCredits === 0) {
        badge.className = 'admin-creditos-badge sin-creditos';
        badge.innerHTML += ` — <em>sin créditos de ${disc === 'CYCLING' ? 'Cycling' : 'Pilates'}</em>`;
      } else {
        badge.className = 'admin-creditos-badge';
      }
    } catch(_) { badge.textContent = ''; }
  }
}

function closeClienteDropdown(e) {
  if (!e.target.closest('#searchableSelect')) {
    document.getElementById('ssDropdown')?.classList.remove('open');
  }
}

function resetClienteSearch() {
  clientesList = [];
  selectedClienteId = null;
  const input = document.getElementById('clienteSearch');
  const dropdown = document.getElementById('ssDropdown');
  if (input)    { input.value = ''; input.disabled = false; }
  if (dropdown) { dropdown.innerHTML = ''; dropdown.classList.remove('open'); }
  const hiddenId = document.getElementById('adminClienteId');
  if (hiddenId) hiddenId.value = '';
  document.removeEventListener('click', closeClienteDropdown);
}

/* ═══════════════════════════════════════════
   AGENDA — NAVEGACIÓN
═══════════════════════════════════════════ */
function prevWeek() { if (weekOffset > 0) { weekOffset--; buildAgenda(); } }
function nextWeek() { weekOffset++; buildAgenda(); }

function filterAgenda(btn, f) {
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  buildAgenda(f);
}

/* ═══════════════════════════════════════════
   INSTRUCTORES (carga desde API)
═══════════════════════════════════════════ */
async function loadInstructores() {
  try {
    const instructores = await api('GET', '/instructores');
    const grid = document.querySelector('.instructores-grid');
    grid.innerHTML = instructores.map(i => {
      const foto = i.fotoUrl
        ? `<img src="${imgSrc(i.fotoUrl)}" class="instructor-photo-img" alt="${i.nombre}">`
        : `<div class="instructor-photo-ph"><span>${i.nombre.charAt(0)}</span></div>`;
      return `
      <div class="instructor-card">
        ${foto}
        <div class="instructor-name">${i.nombre} ${i.apellido}</div>
        <div class="instructor-esp">${i.especialidad === 'SPINNING' ? 'Indoor Cycling' : 'Pilates · Reformer'}</div>
        <p class="instructor-bio">${i.bio}</p>
      </div>`;
    }).join('');
  } catch(e) {
    // Mantiene contenido estático si la API no responde
  }
}

/* ═══════════════════════════════════════════
   NAV SCROLL
═══════════════════════════════════════════ */
window.addEventListener('scroll', () => {
  document.getElementById('nav').classList.toggle('solid', window.scrollY > 40);
});

/* ═══════════════════════════════════════════
   MOBILE DRAWER
═══════════════════════════════════════════ */
function openDrawer()  { document.getElementById('navDrawer').classList.add('open'); }
function closeDrawer() { document.getElementById('navDrawer').classList.remove('open'); }

/* ═══════════════════════════════════════════
   AUTH MODAL
═══════════════════════════════════════════ */
let authState  = { tab: 'login' };
let resetToken = null;

function reservarLugar() {
  if (getToken()) {
    document.getElementById('agenda').scrollIntoView({ behavior: 'smooth' });
  } else {
    openAuth('register');
  }
}

function openAuth(tab) {
  document.getElementById('authOverlay').classList.add('show');
  switchAuth(tab || 'login');
  clearAlert();
}
function closeAuth()         { document.getElementById('authOverlay').classList.remove('show'); }
function overlayClick(e)     { if (e.target === document.getElementById('authOverlay')) closeAuth(); }

function switchAuth(tab) {
  authState.tab = tab;
  const isLogin    = tab === 'login';
  const isRegister = tab === 'register';
  const isReset    = tab === 'reset';
  const isNueva    = tab === 'nuevaPassword';
  const showTabs   = isLogin || isRegister;

  document.getElementById('fLogin').style.display         = isLogin    ? 'block' : 'none';
  document.getElementById('fRegister').style.display      = isRegister ? 'block' : 'none';
  document.getElementById('fReset').style.display         = isReset    ? 'block' : 'none';
  document.getElementById('fNuevaPassword').style.display = isNueva    ? 'block' : 'none';

  document.getElementById('mtLogin').classList.toggle('active', isLogin);
  document.getElementById('mtRegister').classList.toggle('active', isRegister);
  document.querySelector('.modal-tabs').style.display = showTabs ? '' : 'none';
  document.getElementById('formDivider').style.display   = showTabs ? '' : 'none';

  const titles = {
    login:         ['Bienvenido',           'Accede para reservar tu lugar'],
    register:      ['Crea tu cuenta',       'Únete a The Room hoy mismo'],
    reset:         ['Recuperar contraseña', 'Te enviaremos un enlace a tu correo'],
    nuevaPassword: ['Nueva contraseña',     'Elige una contraseña segura'],
  };
  const [title, sub] = titles[tab] || titles.login;
  document.getElementById('mTitle').textContent = title;
  document.getElementById('mSub').textContent   = sub;

  const switchMap = {
    login:         '¿No tienes cuenta? <a onclick="switchAuth(\'register\')">Regístrate gratis</a>',
    register:      '¿Ya tienes cuenta? <a onclick="switchAuth(\'login\')">Inicia sesión</a>',
    reset:         '',
    nuevaPassword: '',
  };
  document.getElementById('mSwitch').innerHTML = switchMap[tab] ?? '';
  clearAlert();
}

function showAlert(msg, type = '') {
  const el = document.getElementById('mAlert');
  el.textContent = msg;
  el.className   = 'modal-alert show' + (type ? ' ' + type : '');
}
function clearAlert() { document.getElementById('mAlert').className = 'modal-alert'; }
function validEmail(e) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e); }

/* ── Login ── */
async function doLogin() {
  const email = document.getElementById('lEmail').value.trim();
  const pass  = document.getElementById('lPass').value;
  if (!email || !pass) { showAlert('Completa todos los campos.'); return; }
  if (!validEmail(email)) { showAlert('Correo electrónico inválido.'); return; }

  const btn = document.querySelector('#fLogin .btn-submit');
  btn.disabled = true; btn.textContent = 'Verificando...';

  try {
    const data = await api('POST', '/auth/login', { email, password: pass });
    saveSession(data);
    onLoginSuccess({ name: `${data.nombre} ${data.apellido}`, email: data.email, rol: data.rol });
  } catch(e) {
    showAlert(e.message || 'Credenciales incorrectas. Intenta de nuevo.');
  } finally {
    btn.disabled = false; btn.textContent = 'Iniciar sesión';
  }
}

/* ── Recuperar contraseña ── */
async function doSolicitarReset() {
  const email = document.getElementById('resetEmail').value.trim();
  if (!email)              { showAlert('Ingresa tu correo electrónico.'); return; }
  if (!validEmail(email))  { showAlert('Correo electrónico inválido.'); return; }

  const btn = document.querySelector('#fReset .btn-submit');
  btn.disabled = true; btn.textContent = 'Enviando...';

  try {
    await api('POST', '/auth/solicitar-reset', { email });
    showAlert('Si el correo está registrado, recibirás el enlace en breve.', 'success');
    document.getElementById('resetEmail').value = '';
  } catch (e) {
    showAlert(e.message || 'No se pudo enviar el enlace. Intenta de nuevo.');
  } finally {
    btn.disabled = false; btn.textContent = 'Enviar enlace de recuperación';
  }
}

async function doResetPassword() {
  const pass    = document.getElementById('npPass').value;
  const confirm = document.getElementById('npPassConfirm').value;
  if (!pass || !confirm)  { showAlert('Completa todos los campos.'); return; }
  if (pass.length < 8)    { showAlert('La contraseña debe tener al menos 8 caracteres.'); return; }
  if (pass !== confirm)   { showAlert('Las contraseñas no coinciden.'); return; }

  const btn = document.querySelector('#fNuevaPassword .btn-submit');
  btn.disabled = true; btn.textContent = 'Actualizando...';

  try {
    await api('POST', '/auth/reset-password', { token: resetToken, nuevaPassword: pass });
    showAlert('¡Contraseña actualizada! Ya puedes iniciar sesión.', 'success');
    resetToken = null;
    setTimeout(() => switchAuth('login'), 2500);
  } catch (e) {
    showAlert(e.message || 'El enlace no es válido o ya expiró.');
  } finally {
    btn.disabled = false; btn.textContent = 'Cambiar contraseña';
  }
}

/* ── Register ── */
async function doRegister() {
  const nombre   = document.getElementById('rName').value.trim();
  const apellido = document.getElementById('rLast').value.trim();
  const email    = document.getElementById('rEmail').value.trim();
  const pass     = document.getElementById('rPass').value;
  const telefono = document.getElementById('rPhone').value.trim();
  const terms    = document.getElementById('rTerms').checked;

  if (!nombre || !apellido || !email || !pass) { showAlert('Completa todos los campos requeridos.'); return; }
  if (!validEmail(email)) { showAlert('Correo electrónico inválido.'); return; }
  if (pass.length < 8)    { showAlert('La contraseña debe tener al menos 8 caracteres.'); return; }
  if (!terms)             { showAlert('Debes aceptar los términos para continuar.'); return; }

  const btn = document.querySelector('#fRegister .btn-submit');
  btn.disabled = true; btn.textContent = 'Creando cuenta...';

  try {
    const data = await api('POST', '/auth/register', { nombre, apellido, email, password: pass, telefono });
    saveSession(data);
    onLoginSuccess({ name: `${data.nombre} ${data.apellido}`, email: data.email, rol: data.rol, isNew: true });
  } catch(e) {
    showAlert(e.message || 'No fue posible crear la cuenta.');
  } finally {
    btn.disabled = false; btn.textContent = 'Crear mi cuenta';
  }
}

/* ── Post-login ── */
let currentUser = null;

function buildNavUser(u) {
  const initials = u.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
  return `
    <div style="position:relative">
      <button class="user-chip" onclick="toggleUDrop()">
        <div class="chip-avatar">${initials}</div>
        <span class="chip-name">${u.name.split(' ')[0]}</span>
        <span class="chip-caret">▾</span>
      </button>
      <div class="user-dropdown" id="udrop">
        <div class="udrop-header">
          <div class="udrop-name">${u.name}</div>
          <div class="udrop-email">${u.email}</div>
          ${u.rol !== 'ADMIN' ? `
          <div class="udrop-creditos-label">Créditos disponibles</div>
          <div class="udrop-creditos" id="creditosBadge">cargando...</div>` : ''}
        </div>
        <a class="udrop-link" onclick="openMisReservaciones()">Mis reservaciones</a>
        ${u.rol === 'ADMIN' ? `
          <a class="udrop-link" onclick="openGestionClases()">Gestionar clases</a>
          <a class="udrop-link" onclick="openGestionInstructores()">Gestionar instructores</a>
          <a class="udrop-link" onclick="openGestionPaquetes()">Gestionar paquetes</a>
          <a class="udrop-link" onclick="openCobroEfectivo()">Cobrar en efectivo</a>
          <a class="udrop-link" onclick="openEquipo()">Gestionar equipo</a>
          <a class="udrop-link" onclick="openEnCurso()">Clases en curso</a>
          <a class="udrop-link" onclick="openClientes()">Clientes y créditos</a>
          <a class="udrop-link" onclick="openHistorial()">Historial de ventas</a>` : ''}
        <a class="udrop-link danger" onclick="doLogout()">Cerrar sesión</a>
      </div>
    </div>`;
}

function buildDrawerAuth(u) {
  if (!u) return `
    <button class="btn-outline" onclick="closeDrawer();openAuth('login')">Iniciar sesión</button>
    <button class="btn-primary" onclick="closeDrawer();openAuth('register')">Unirme</button>`;
  const adminLinks = u.rol === 'ADMIN' ? `
    <a class="drawer-user-link" onclick="closeDrawer();openGestionClases()">Gestionar clases</a>
    <a class="drawer-user-link" onclick="closeDrawer();openGestionInstructores()">Gestionar instructores</a>
    <a class="drawer-user-link" onclick="closeDrawer();openGestionPaquetes()">Gestionar paquetes</a>
    <a class="drawer-user-link" onclick="closeDrawer();openCobroEfectivo()">Cobrar en efectivo</a>
    <a class="drawer-user-link" onclick="closeDrawer();openEquipo()">Gestionar equipo</a>
    <a class="drawer-user-link" onclick="closeDrawer();openEnCurso()">Clases en curso</a>
    <a class="drawer-user-link" onclick="closeDrawer();openClientes()">Clientes y créditos</a>
    <a class="drawer-user-link" onclick="closeDrawer();openHistorial()">Historial de ventas</a>` : `
    <a class="drawer-user-link" onclick="closeDrawer();openMisReservaciones()">Mis reservaciones</a>`;
  return `
    <div class="drawer-user-info">
      <span class="drawer-user-name">${u.name}</span>
      <span class="drawer-user-email">${u.email}</span>
      ${u.rol !== 'ADMIN' ? `
      <div class="udrop-creditos-label">Créditos disponibles</div>
      <div class="udrop-creditos" id="creditosBadgeDrawer">cargando...</div>` : ''}
    </div>
    ${adminLinks}
    <a class="drawer-user-link danger" onclick="closeDrawer();doLogout()">Cerrar sesión</a>`;
}

function updateDrawerAuth(u) {
  document.getElementById('drawerAuth').innerHTML = buildDrawerAuth(u);
}

function onLoginSuccess(u) {
  currentUser = u;
  closeAuth();
  document.getElementById('navActions').innerHTML = buildNavUser(u);
  updateDrawerAuth(u);
  loadCreditos();
  showToast(
    u.isNew ? '¡Cuenta creada!' : '¡Bienvenido de vuelta!',
    u.isNew ? `Tu cuenta ha sido creada, ${u.name.split(' ')[0]}.` : `Hola de nuevo, ${u.name.split(' ')[0]}.`,
    u.isNew ? 'success' : ''
  );
}

function toggleUDrop() { document.getElementById('udrop')?.classList.toggle('open'); }

function doLogout() {
  showConfirm(
    'Cerrar sesión',
    '¿Seguro que deseas cerrar tu sesión?',
    'Cerrar sesión',
    'btn-danger',
    _execLogout
  );
}

function _execLogout() {
  clearSession();
  currentUser = null;
  document.getElementById('navActions').innerHTML = `
    <button class="nav-btn-ghost" onclick="openAuth('login')">Iniciar sesión</button>
    <button class="nav-btn-solid" onclick="openAuth('register')">Unirme</button>`;
  updateDrawerAuth(null);
  showToast('Sesión cerrada', 'Hasta pronto.');
}

document.addEventListener('click', e => {
  if (!e.target.closest('.user-chip') && !e.target.closest('#udrop'))
    document.getElementById('udrop')?.classList.remove('open');
});

/* ═══════════════════════════════════════════
   MIS RESERVACIONES
═══════════════════════════════════════════ */
let allAdminReservaciones  = [];
let reservClientesList     = [];
let reservSelectedClienteId = null;

async function openMisReservaciones() {
  document.getElementById('udrop')?.classList.remove('open');
  const overlay    = document.getElementById('reservOverlay');
  const title      = overlay.querySelector('.modal-title');
  const sub        = overlay.querySelector('.modal-sub');
  const body       = document.getElementById('reservBody');

  if (isAdmin()) {
    title.textContent = 'Todas las reservaciones';
    sub.textContent   = 'Gestión de reservaciones del estudio';
    document.getElementById('reservAdminSearch').style.display = 'block';
    _resetReservSs();
  } else {
    document.getElementById('reservAdminSearch').style.display = 'none';
    title.textContent = 'Mis reservaciones';
    sub.textContent   = 'Próximas clases agendadas';
  }

  overlay.classList.add('show');
  body.innerHTML = '<div class="agenda-loading">Cargando reservaciones...</div>';

  try {
    if (isAdmin()) {
      [allAdminReservaciones, reservClientesList] = await Promise.all([
        api('GET', '/admin/reservaciones'),
        reservClientesList.length ? Promise.resolve(reservClientesList) : api('GET', '/admin/usuarios')
      ]);
      _initReservSs();
      _renderReservacionesAdmin(allAdminReservaciones);
    } else {
      const data = await api('GET', '/reservaciones/proximas');
      if (!data.length) {
        body.innerHTML = '<p class="resv-empty">No tienes reservaciones próximas.</p>';
        return;
      }
      const hoyCliente = new Date().toISOString().slice(0, 10);
      body.innerHTML = data.map(r => {
        const pasado = r.fecha < hoyCliente;
        return `
        <div class="resv-item${pasado ? ' pasado' : ''}" id="resv-${r.id}">
          <div class="resv-badge ${r.tipoClase === 'SPINNING' ? 'spin' : 'pilates'}">
            ${r.tipoClase === 'SPINNING' ? 'Indoor Cycling' : 'Pilates'}
          </div>
          <div class="resv-info">
            <div class="resv-date">${formatDate(r.fecha)}</div>
            <div class="resv-hora">${r.hora.replace(/^0/,'')} · ${r.instructor}</div>
            ${pasado ? '<div class="resv-estado pasado">PASADO</div>' : ''}
          </div>
          ${!pasado ? `<button class="resv-cancel" onclick="cancelarReservacion(${r.id}, false)">Cancelar</button>` : ''}
        </div>`;
      }).join('');
    }
  } catch(e) {
    body.innerHTML = `<p class="resv-empty">${e.message}</p>`;
  }
}

function _resetReservSs() {
  reservSelectedClienteId = null;
  const input = document.getElementById('reservSsInput');
  if (input) { input.value = ''; input.placeholder = 'Todos los clientes'; }
  document.getElementById('reservSsDropdown')?.classList.remove('open');
  document.getElementById('reservSearchCount').textContent = '';
  document.removeEventListener('click', _closeReservSsDropdown);
}

function _initReservSs() {
  const input    = document.getElementById('reservSsInput');
  const dropdown = document.getElementById('reservSsDropdown');
  if (!input || input._reservSsInited) return;
  input._reservSsInited = true;

  input.addEventListener('focus', () => {
    _renderReservSsOpciones(reservClientesList);
    dropdown.classList.add('open');
  });

  input.addEventListener('input', () => {
    reservSelectedClienteId = null;
    input.placeholder = 'Todos los clientes';
    const q = input.value.trim().toLowerCase();
    const filtrados = q
      ? reservClientesList.filter(c =>
          `${c.nombre} ${c.apellido}`.toLowerCase().includes(q) ||
          c.email.toLowerCase().includes(q))
      : reservClientesList;
    _renderReservSsOpciones(filtrados);
    dropdown.classList.add('open');
    if (!q) _renderReservacionesAdmin(allAdminReservaciones);
  });

  document.addEventListener('click', _closeReservSsDropdown);
}

function _renderReservSsOpciones(lista) {
  const dropdown = document.getElementById('reservSsDropdown');
  const items = lista.slice(0, 10).map(c => `
    <div class="ss-option" onclick="selectReservCliente(${c.id},'${c.nombre} ${c.apellido}','${c.email}')">
      <div class="ss-option-name">${c.nombre} ${c.apellido}</div>
      <div class="ss-option-email">${c.email}</div>
    </div>`).join('');

  const verTodas = reservSelectedClienteId ? `
    <div class="ss-option resv-ss-todos" onclick="limpiarFiltroReserv()">
      <div class="ss-option-name">— Ver todas las reservaciones</div>
    </div>` : '';

  dropdown.innerHTML = verTodas + (items || '<div class="ss-option ss-empty">Sin resultados</div>');
}

function selectReservCliente(id, nombre, email) {
  reservSelectedClienteId = id;
  const input = document.getElementById('reservSsInput');
  input.value = `${nombre} — ${email}`;
  document.getElementById('reservSsDropdown').classList.remove('open');

  const filtradas = allAdminReservaciones.filter(r => r.usuarioId === id);
  _renderReservacionesAdmin(filtradas, nombre);
}

function limpiarFiltroReserv() {
  reservSelectedClienteId = null;
  const input = document.getElementById('reservSsInput');
  input.value = '';
  input.placeholder = 'Todos los clientes';
  document.getElementById('reservSsDropdown').classList.remove('open');
  _renderReservacionesAdmin(allAdminReservaciones);
}

function filtrarReservacionesAdmin() {
  if (reservSelectedClienteId) {
    const cliente = reservClientesList.find(c => c.id === reservSelectedClienteId);
    const nombre = cliente ? `${cliente.nombre} ${cliente.apellido}` : '';
    _renderReservacionesAdmin(
      allAdminReservaciones.filter(r => r.usuarioId === reservSelectedClienteId),
      nombre
    );
  } else {
    _renderReservacionesAdmin(allAdminReservaciones);
  }
}

function _closeReservSsDropdown(e) {
  const wrap = document.getElementById('reservSsWrap');
  if (wrap && !wrap.contains(e.target))
    document.getElementById('reservSsDropdown')?.classList.remove('open');
}

function _renderReservacionesAdmin(data, clienteNombre) {
  const body  = document.getElementById('reservBody');
  const count = document.getElementById('reservSearchCount');
  const total = allAdminReservaciones.length;

  if (count) {
    count.textContent = clienteNombre
      ? `${data.length} reservacion${data.length !== 1 ? 'es' : ''} de ${clienteNombre}`
      : `${total} reservacion${total !== 1 ? 'es' : ''} en total`;
  }

  if (!data.length) {
    body.innerHTML = `<p class="resv-empty">${
      clienteNombre ? `${clienteNombre} no tiene reservaciones.` : 'No hay reservaciones registradas.'
    }</p>`;
    return;
  }

  const hoy = new Date().toISOString().slice(0, 10);
  body.innerHTML = data.map(r => {
    const pasado = r.fecha < hoy;
    const estadoClass = pasado && r.estado === 'CONFIRMADA' ? 'pasado' : r.estado.toLowerCase();
    const estadoLabel = pasado && r.estado === 'CONFIRMADA' ? 'PASADO' : r.estado.replace('_', ' ');
    const puedeCancelar = r.estado === 'CONFIRMADA' && !pasado;
    return `
    <div class="resv-item${pasado ? ' pasado' : ''}" id="resv-${r.id}">
      <div class="resv-badge ${r.tipoClase === 'SPINNING' ? 'spin' : 'pilates'}">
        ${r.tipoClase === 'SPINNING' ? 'Indoor Cycling' : 'Pilates'}
      </div>
      <div class="resv-info">
        <div class="resv-date">${formatDate(r.fecha)} · ${r.hora.replace(/^0/,'')} · ${r.instructor}</div>
        <div class="resv-hora resv-usuario">${r.usuarioNombre} <span class="resv-email">${r.usuarioEmail}</span></div>
        <div class="resv-estado ${estadoClass}">${estadoLabel}</div>
      </div>
      ${puedeCancelar ? `<button class="resv-cancel" onclick="cancelarReservacion(${r.id}, true)">Cancelar</button>` : ''}
    </div>`;
  }).join('');
}

function cancelarReservacion(id, esAdmin) {
  showConfirm(
    'Cancelar reservación',
    esAdmin
      ? '¿Seguro que deseas cancelar esta reservación? El lugar quedará disponible nuevamente.'
      : 'Esta acción liberará tu lugar en la clase. ¿Deseas continuar?',
    'Sí, cancelar',
    'btn-danger',
    () => doCancelarReservacion(id, esAdmin)
  );
}

async function doCancelarReservacion(id, esAdmin) {
  try {
    const endpoint = esAdmin ? `/admin/reservaciones/${id}` : `/reservaciones/${id}`;
    await api('DELETE', endpoint);

    if (esAdmin) {
      allAdminReservaciones = allAdminReservaciones.filter(r => r.id !== id);
      filtrarReservacionesAdmin();
    } else {
      document.getElementById(`resv-${id}`)?.remove();
      const body = document.getElementById('reservBody');
      if (!body.querySelector('.resv-item'))
        body.innerHTML = '<p class="resv-empty">No tienes reservaciones próximas.</p>';
    }

    showToast('Reservación cancelada', 'El lugar ha sido liberado.');
    buildAgenda();
    if (!esAdmin) loadCreditos();
  } catch(e) {
    showToast('Error al cancelar', e.message);
  }
}

function formatDate(dateStr) {
  const [y, m, d] = dateStr.split('-');
  return `${parseInt(d)} de ${MONTH_NAMES[parseInt(m)-1]} ${y}`;
}

function closeReserv()      { document.getElementById('reservOverlay').classList.remove('show'); }
function reservOverlayClick(e) { if (e.target === document.getElementById('reservOverlay')) closeReserv(); }

/* ═══════════════════════════════════════════
   GESTIONAR CLASES (admin)
═══════════════════════════════════════════ */
const DIA_LABEL = { LUNES:'Lun', MARTES:'Mar', MIERCOLES:'Mié', JUEVES:'Jue', VIERNES:'Vie' };

let clasesAdminData  = [];
let instructoresData = [];
let clasesAdminFiltro = 'todos';

async function openGestionClases() {
  document.getElementById('udrop')?.classList.remove('open');
  document.getElementById('clasesOverlay').classList.add('show');
  const body = document.getElementById('clasesAdminBody');
  body.innerHTML = '<div class="agenda-loading">Cargando clases...</div>';

  try {
    [clasesAdminData, instructoresData] = await Promise.all([
      api('GET', '/admin/clases'),
      api('GET', '/admin/instructores')
    ]);
    clasesAdminFiltro = 'todos';
    document.querySelectorAll('#clasesFilterBar .filter-btn').forEach((b,i) => b.classList.toggle('active', i===0));
    renderClasesAdmin();
  } catch(e) {
    body.innerHTML = `<p class="resv-empty">${e.message}</p>`;
  }
}

function filtrarClasesAdmin(btn, filtro) {
  clasesAdminFiltro = filtro;
  document.querySelectorAll('#clasesFilterBar .filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderClasesAdmin();
}

function renderClasesAdmin() {
  const body = document.getElementById('clasesAdminBody');

  // Filtrar solo Lun–Vie
  let lista = clasesAdminData.filter(c => c.diaSemana in DIA_LABEL);
  if (clasesAdminFiltro === 'inactivas') {
    lista = lista.filter(c => !c.activo);
  } else if (clasesAdminFiltro !== 'todos') {
    lista = lista.filter(c => c.tipo === clasesAdminFiltro);
  }

  if (!lista.length) { body.innerHTML = '<p class="resv-empty">Sin clases para mostrar.</p>'; return; }

  const horas = [...new Set(clasesAdminData.filter(c => c.diaSemana in DIA_LABEL).map(c => c.hora))].sort();

  // Calcular fechas Lun–Vie de la semana actual
  const monday  = getMonday(0);
  const friday  = new Date(monday); friday.setDate(monday.getDate() + 4);
  const todayMs = new Date().setHours(0,0,0,0);
  const fmtD    = d => `${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`;
  const weekStr = `${fmtD(monday)} — ${fmtD(friday)} ${friday.getFullYear()}`;

  // Fechas por columna con flag isPast
  const colDates = DIAS_ORDEN.map((_, i) => {
    const d = new Date(monday); d.setDate(monday.getDate() + i);
    return { num: d.getDate(), isPast: d.getTime() < todayMs };
  });

  // Construir grid calendario
  let html = `<div class="admin-week-label">${weekStr}</div>`;
  html += '<div class="admin-cal-grid">';

  // Esquina
  html += '<div class="aw-corner"></div>';

  // Encabezados de días con fecha y marcador de pasado
  DIAS_ORDEN.forEach((dia, i) => {
    html += `<div class="aw-day${colDates[i].isPast ? ' day-past' : ''}">
      <span class="aw-day-name">${DIA_LABEL[dia]}</span>
      <span class="aw-day-num">${colDates[i].num}</span>
    </div>`;
  });

  // Filas de horas
  horas.forEach(hora => {
    html += `<div class="aw-time">${hora.replace(/^0/,'')}</div>`;
    DIAS_ORDEN.forEach((dia, i) => {
      const isPast = colDates[i].isPast;
      const cls = lista.find(c => c.hora === hora && c.diaSemana === dia);
      if (cls) {
        html += `<div class="aw-cell${isPast ? ' cell-past' : ''}">${renderAdminPill(cls, isPast)}</div>`;
      } else {
        const oculta = clasesAdminData.find(c => c.hora === hora && c.diaSemana === dia);
        html += `<div class="aw-cell${isPast ? ' cell-past' : ''}">${oculta ? '<div class="admin-pill-hidden"></div>' : ''}</div>`;
      }
    });
  });

  html += '</div>';
  body.innerHTML = html;
}

function renderAdminPill(c, isPast = false) {
  const isSpin  = c.tipo === 'SPINNING';
  const sinInst = !c.instructorNombre;
  const tipo    = isSpin ? 'spin' : 'pilates';
  return `
    <div class="admin-pill ${tipo}${c.activo ? '' : ' inactiva'}${isPast ? ' past' : ''}" id="ap-${c.id}">
      <span class="ap-nombre">${isSpin ? 'Indoor Cycling' : 'Pilates'}</span>
      <span class="ap-inst${sinInst ? ' sin' : ''}">${sinInst ? 'Sin asignar' : c.instructorNombre}</span>
      ${isPast
        ? `<span class="ap-past-label">Pasado</span>`
        : `<div class="ap-btns">
             <button class="ap-btn ap-btn-edit" title="Editar clase"
                     onclick="openEditarClase(${c.id})">✎</button>
             <button class="ap-btn ${c.activo ? 'ap-btn-deactivate' : 'ap-btn-activate'}"
                     onclick="toggleClaseActivo(${c.id})">
               ${c.activo ? 'Desactivar' : 'Activar'}
             </button>
             <button class="ap-btn ap-btn-delete" title="Eliminar clase"
                     onclick="confirmarEliminarClase(${c.id})">✕</button>
           </div>`
      }
    </div>`;
}

function toggleInlineInstructor(claseId) {
  const container = document.getElementById(`inline-${claseId}`);
  if (container.innerHTML) { container.innerHTML = ''; return; }

  const opciones = instructoresData.map(i =>
    `<option value="${i.id}">${i.nombre} ${i.apellido}</option>`).join('');

  container.innerHTML = `
    <div class="ap-inst-form">
      <select id="sel-inst-${claseId}">
        <option value="">Sin asignar</option>
        ${opciones}
      </select>
      <button class="ap-btn on" onclick="guardarInstructor(${claseId})">✓</button>
      <button class="ap-btn" onclick="document.getElementById('inline-${claseId}').innerHTML=''">✕</button>
    </div>`;

  const claseActual = clasesAdminData.find(c => c.id === claseId);
  if (claseActual?.instructorId)
    document.getElementById(`sel-inst-${claseId}`).value = claseActual.instructorId;
}

function guardarInstructor(claseId) {
  const selVal = document.getElementById(`sel-inst-${claseId}`).value;
  const nombre = selVal
    ? instructoresData.find(i => i.id === parseInt(selVal))?.nombre ?? 'seleccionado'
    : 'Sin asignar';
  showConfirm(
    'Cambiar instructor',
    `¿Confirmas asignar a "${nombre}" como instructor de esta clase?`,
    'Confirmar',
    '',
    () => doGuardarInstructor(claseId)
  );
}

async function doGuardarInstructor(claseId) {
  const selVal = document.getElementById(`sel-inst-${claseId}`)?.value;
  const instructorId = selVal ? parseInt(selVal) : null;
  try {
    const updated = await api('PATCH', `/admin/clases/${claseId}/instructor`, { instructorId });
    const idx = clasesAdminData.findIndex(c => c.id === claseId);
    if (idx !== -1) clasesAdminData[idx] = updated;
    document.getElementById(`ap-${claseId}`).outerHTML = renderAdminPill(updated);
    showToast('Instructor actualizado', updated.instructorNombre ?? 'Desasignado.', 'success');
  } catch(e) {
    showToast('Error', e.message);
  }
}

function toggleClaseActivo(claseId) {
  const clase = clasesAdminData.find(c => c.id === claseId);
  if (!clase) return;

  if (clase.activo) {
    showConfirm(
      'Desactivar clase',
      `La clase de ${clase.tipo === 'SPINNING' ? 'Indoor Cycling' : 'Pilates'} a las ${clase.hora.replace(/^0/,'')} dejará de aparecer en el calendario para reservaciones.`,
      'Desactivar',
      'btn-danger',
      () => doToggleClaseActivo(claseId)
    );
  } else {
    showConfirm(
      'Activar clase',
      `La clase de ${clase.tipo === 'SPINNING' ? 'Indoor Cycling' : 'Pilates'} a las ${clase.hora.replace(/^0/,'')} volverá a aparecer en el calendario y estará disponible para reservaciones.`,
      'Activar',
      'btn-success',
      () => doToggleClaseActivo(claseId)
    );
  }
}

async function doToggleClaseActivo(claseId) {
  try {
    const updated = await api('PATCH', `/admin/clases/${claseId}/toggle`);
    const idx = clasesAdminData.findIndex(c => c.id === claseId);
    if (idx !== -1) clasesAdminData[idx] = updated;
    document.getElementById(`ap-${claseId}`).outerHTML = renderAdminPill(updated);
    showToast(
      updated.activo ? 'Clase activada' : 'Clase desactivada',
      `La clase ha sido ${updated.activo ? 'habilitada' : 'deshabilitada'} en el calendario.`,
      updated.activo ? 'success' : ''
    );
    buildAgenda();
  } catch(e) {
    showToast('Error', e.message);
  }
}

function closeClases()        { document.getElementById('clasesOverlay').classList.remove('show'); }
function clasesOverlayClick(e){ if (e.target === document.getElementById('clasesOverlay')) closeClases(); }

/* ═══════════════════════════════════════════
   CREAR / EDITAR CLASE (admin)
═══════════════════════════════════════════ */
function openNuevaClase() {
  document.getElementById('claseFormId').value       = '';
  document.getElementById('claseFormTitle').textContent = 'Nueva clase';
  document.getElementById('claseFormTipo').value     = 'SPINNING';
  document.getElementById('claseFormDia').value      = 'LUNES';
  document.getElementById('claseFormHora').value     = '';
  document.getElementById('claseFormCupo').value     = 7;
  document.getElementById('claseFormAlert').textContent = '';
  _populateInstructorSelect(null);
  document.getElementById('claseFormOverlay').classList.add('show');
}

function openEditarClase(id) {
  const c = clasesAdminData.find(x => x.id === id);
  if (!c) return;
  document.getElementById('claseFormId').value          = c.id;
  document.getElementById('claseFormTitle').textContent = 'Editar clase';
  document.getElementById('claseFormTipo').value        = c.tipo;
  document.getElementById('claseFormDia').value         = c.diaSemana;
  document.getElementById('claseFormHora').value        = c.hora;
  document.getElementById('claseFormCupo').value        = c.cupoTotal;
  document.getElementById('claseFormAlert').textContent = '';
  _populateInstructorSelect(c.instructorId);
  document.getElementById('claseFormOverlay').classList.add('show');
}

function _populateInstructorSelect(selectedId) {
  const sel = document.getElementById('claseFormInstructor');
  sel.innerHTML = '<option value="">Sin asignar</option>' +
    instructoresData.map(i => `<option value="${i.id}">${i.nombre} ${i.apellido}</option>`).join('');
  if (selectedId) sel.value = selectedId;
}

async function doGuardarClase() {
  const alertEl = document.getElementById('claseFormAlert');
  const id       = document.getElementById('claseFormId').value;
  const hora     = document.getElementById('claseFormHora').value.trim();

  if (!/^\d{2}:\d{2}$/.test(hora)) {
    alertEl.textContent = 'La hora debe tener formato HH:mm (ej. 06:00)';
    return;
  }

  const body = {
    tipo:         document.getElementById('claseFormTipo').value,
    diaSemana:    document.getElementById('claseFormDia').value,
    hora,
    instructorId: document.getElementById('claseFormInstructor').value
                    ? parseInt(document.getElementById('claseFormInstructor').value)
                    : null,
    cupoTotal:    parseInt(document.getElementById('claseFormCupo').value)
  };

  try {
    let result;
    if (id) {
      result = await api('PUT', `/admin/clases/${id}`, body);
      const idx = clasesAdminData.findIndex(c => c.id === result.id);
      if (idx !== -1) clasesAdminData[idx] = result;
    } else {
      result = await api('POST', '/admin/clases', body);
      clasesAdminData.push(result);
    }
    closeClaseForm();
    renderClasesAdmin();
    buildAgenda();
    showToast(id ? 'Clase actualizada' : 'Clase creada',
              `${result.tipo === 'SPINNING' ? 'Indoor Cycling' : 'Pilates'} ${result.hora} — ${result.diaSemana}`,
              'success');
  } catch(e) {
    alertEl.textContent = e.message;
  }
}

function confirmarEliminarClase(id) {
  const c = clasesAdminData.find(x => x.id === id);
  if (!c) return;
  showConfirm(
    'Eliminar clase',
    `¿Seguro que deseas eliminar la clase de ${c.tipo === 'SPINNING' ? 'Indoor Cycling' : 'Pilates'} a las ${c.hora} del ${c.diaSemana.charAt(0) + c.diaSemana.slice(1).toLowerCase()}? Esta acción no se puede deshacer.`,
    'Eliminar',
    'btn-danger',
    () => doEliminarClase(id)
  );
}

async function doEliminarClase(id) {
  try {
    await api('DELETE', `/admin/clases/${id}`);
    clasesAdminData = clasesAdminData.filter(c => c.id !== id);
    renderClasesAdmin();
    buildAgenda();
    showToast('Clase eliminada', 'La clase fue removida del calendario.', '');
  } catch(e) {
    showToast('No se pudo eliminar', e.message);
  }
}

function closeClaseForm() {
  document.getElementById('claseFormOverlay').classList.remove('show');
}
function claseFormOverlayClick(e) {
  if (e.target === document.getElementById('claseFormOverlay')) closeClaseForm();
}

/* ═══════════════════════════════════════════
   PAGOS — OPENPAY
═══════════════════════════════════════════ */
// Llave pública de OpenPay (frontend — no es secreta)
const OPENPAY_MERCHANT_ID = 'men5pa8eci4oyvpzolxe';
const OPENPAY_PUBLIC_KEY  = 'pk_8ae07f8dbe4342d7a8297a7be69eb71e';
const OPENPAY_SANDBOX     = true;

let misCreditosCycling = 0;
let misCreditosCyclingVencen = null;
let misCreditosPilates = 0;
let misCreditosPilatesVencen = null;

let _openpayDeviceSessionId = null;

function initOpenpay() {
  if (typeof OpenPay === 'undefined') return;
  OpenPay.setId(OPENPAY_MERCHANT_ID);
  OpenPay.setApiKey(OPENPAY_PUBLIC_KEY);
  OpenPay.setSandboxMode(OPENPAY_SANDBOX);
  _openpayDeviceSessionId = OpenPay.deviceData.setup('pago-form', 'deviceSessionId');
}

// Llamada directa — los scripts están al final del body, el DOM ya está listo
if (typeof OpenPay !== 'undefined') initOpenpay();


async function loadCreditos() {
  if (!getToken() || !currentUser || currentUser.rol === 'ADMIN') return;
  try {
    const data = await api('GET', '/pagos/mis-creditos');
    misCreditosCycling       = data.creditosCycling ?? 0;
    misCreditosCyclingVencen = data.creditosCyclingVencen ?? null;
    misCreditosPilates       = data.creditosPilates ?? 0;
    misCreditosPilatesVencen = data.creditosPilatesVencen ?? null;
    _actualizarCreditosBadge();
  } catch(_) {}
}

function _fmtFecha(iso) {
  if (!iso) return null;
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

function _actualizarCreditosBadge() {
  const hoy = new Date().toISOString().slice(0, 10);
  const cycExp = misCreditosCycling > 0 && misCreditosCyclingVencen && misCreditosCyclingVencen < hoy;
  const pilExp = misCreditosPilates > 0 && misCreditosPilatesVencen && misCreditosPilatesVencen < hoy;
  const cycSuffix = cycExp
    ? ' <span class="creditos-vence creditos-exp">· Expirado</span>'
    : (misCreditosCycling > 0 && misCreditosCyclingVencen ? ` <span class="creditos-vence">· ${_fmtFecha(misCreditosCyclingVencen)}</span>` : '');
  const pilSuffix = pilExp
    ? ' <span class="creditos-vence creditos-exp">· Expirado</span>'
    : (misCreditosPilates > 0 && misCreditosPilatesVencen ? ` <span class="creditos-vence">· ${_fmtFecha(misCreditosPilatesVencen)}</span>` : '');
  const html =
    `<span>Cycling: <strong>${misCreditosCycling}</strong>${cycSuffix}</span>` +
    `<span class="udrop-creditos-sep">·</span>` +
    `<span>Pilates: <strong>${misCreditosPilates}</strong>${pilSuffix}</span>`;
  const badge = document.getElementById('creditosBadge');
  if (badge) badge.innerHTML = html;
  const badgeDrawer = document.getElementById('creditosBadgeDrawer');
  if (badgeDrawer) badgeDrawer.innerHTML = html;
}

let _PAQUETES_INFO = {};

async function loadPaquetes() {
  try {
    const lista = await api('GET', '/pagos/paquetes');
    _PAQUETES_INFO = {};
    const cycling = [], pilates = [];
    lista.forEach(p => {
      _PAQUETES_INFO[p.id] = { nombre: p.nombre, precio: p.precio, clases: p.numClases, disciplina: p.disciplina, esMensual: p.esMensual, vigenciaDias: p.vigenciaDias };
      if (p.disciplina === 'CYCLING') cycling.push(p);
      else pilates.push(p);
    });
    _renderPkgList('pkgListCycling', cycling);
    _renderPkgList('pkgListPilates', pilates);
  } catch(_) {}
}

function _renderPkgList(containerId, paquetes) {
  const grid = document.getElementById(containerId);
  if (!grid) return;
  grid.innerHTML = paquetes.map(p => {
    const featured  = p.esMensual ? ' featured' : '';
    const badge     = p.esMensual ? '<span class="paquete-badge">Más popular</span>' : '';
    const label     = p.esMensual ? 'Plan mensual' : (p.numClases > 1 ? 'clases' : 'clase');
    const precio    = `$${Number(p.precio).toLocaleString('es-MX')}`;
    const vigencia  = p.vigenciaDias <= 15 ? `${p.vigenciaDias} días` : '1 mes';
    const icon      = containerId.includes('Cycling') ? '🚴' : '🧘';
    return `<div class="paquete-card${featured}">
      ${badge}
      <div class="paquete-icon">${icon}</div>
      <div class="paquete-clases">${p.esMensual ? '∞' : p.numClases}</div>
      <span class="paquete-label">${label.toUpperCase()}</span>
      <div class="paquete-price">${precio}</div>
      <span class="paquete-vigencia">Vigencia: ${vigencia}</span>
      <button class="paquete-btn" onclick="seleccionarPaquete(${p.id},${p.numClases},${p.precio})">Seleccionar</button>
    </div>`;
  }).join('');
}


function seleccionarPaquete(paqueteId, numClases, precio) {
  if (!getToken() || !currentUser) {
    openAuth('login');
    return;
  }
  if (currentUser.rol === 'ADMIN') {
    showToast('Solo clientes', 'Los administradores no compran paquetes.');
    return;
  }

  const info = _PAQUETES_INFO[paqueteId] || { nombre: `${numClases} clase${numClases > 1 ? 's' : ''}`, precio, clases: numClases, esMensual: false };

  document.getElementById('pendingPaqueteId').value   = paqueteId;
  document.getElementById('pagoNumero').value         = '';
  document.getElementById('pagoMes').value            = '';
  document.getElementById('pagoAnio').value           = '';
  document.getElementById('pagoCVV').value            = '';
  document.getElementById('pagoTitular').value        = '';
  document.getElementById('pagoBtnPagar').disabled    = false;
  document.getElementById('pagoBtnPagar').textContent = `Pagar $${Number(info.precio).toLocaleString('es-MX')} MXN`;

  document.getElementById('pagoPlanCard').innerHTML = `
    <div class="pago-plan-header">
      <div class="pago-plan-num">${info.esMensual ? 'MES' : info.clases}</div>
      <div class="pago-plan-details">
        <div class="pago-plan-nombre">${info.nombre}</div>
        <div class="pago-plan-tag">Indoor Cycling &amp; Pilates · Sin contratos</div>
      </div>
      <div class="pago-plan-precio">$${Number(info.precio).toLocaleString('es-MX')}<span class="pago-plan-moneda"> MXN</span></div>
    </div>`;

  document.getElementById('pagoAlert').textContent = '';
  document.getElementById('pagoOverlay').classList.add('show');
}

function solicitarConfirmacionPago() {
  const numero  = document.getElementById('pagoNumero').value.replace(/\s/g, '');
  const mes     = document.getElementById('pagoMes').value.trim();
  const anio    = document.getElementById('pagoAnio').value.trim();
  const cvv     = document.getElementById('pagoCVV').value.trim();
  const titular = document.getElementById('pagoTitular').value.trim();
  const alertEl = document.getElementById('pagoAlert');
  alertEl.textContent = '';

  if (!numero || !mes || !anio || !cvv || !titular) {
    alertEl.textContent = 'Completa todos los campos de la tarjeta.';
    return;
  }
  if (numero.length < 15) { alertEl.textContent = 'Número de tarjeta inválido.'; return; }

  const paqueteId = parseInt(document.getElementById('pendingPaqueteId').value);
  const info = _PAQUETES_INFO[paqueteId] || {};
  const ultimos4 = numero.slice(-4);
  const monto = Number(info.precio || 0).toLocaleString('es-MX', { style: 'currency', currency: 'MXN' });

  document.getElementById('pagoConfirmResumen').innerHTML = `
    <p class="pago-confirm-titulo">¿Confirmar pago?</p>
    <div class="pago-confirm-fila"><span>Paquete</span><strong>${info.nombre || '—'}</strong></div>
    <div class="pago-confirm-fila"><span>Titular</span><strong>${titular}</strong></div>
    <div class="pago-confirm-fila"><span>Tarjeta</span><strong>•••• ${ultimos4}</strong></div>
    <div class="pago-confirm-fila pago-confirm-total"><span>Total</span><strong>${monto}</strong></div>`;

  document.getElementById('pago-form').style.display = 'none';
  document.getElementById('pagoConfirmPanel').style.display = 'block';
}

function cancelarConfirmacionPago() {
  document.getElementById('pagoConfirmPanel').style.display = 'none';
  document.getElementById('pago-form').style.display = 'block';
}

function procesarPago() {
  const numero   = document.getElementById('pagoNumero').value.replace(/\s/g, '');
  const mes      = document.getElementById('pagoMes').value.trim();
  const anio     = document.getElementById('pagoAnio').value.trim();
  const cvv      = document.getElementById('pagoCVV').value.trim();
  const titular  = document.getElementById('pagoTitular').value.trim();
  const alertEl  = document.getElementById('pagoAlert');
  alertEl.textContent = '';

  if (!numero || !mes || !anio || !cvv || !titular) {
    alertEl.textContent = 'Completa todos los campos de la tarjeta.';
    return;
  }
  if (numero.length < 15) { alertEl.textContent = 'Número de tarjeta inválido.'; return; }

  const btn = document.getElementById('pagoBtnConfirmar');
  btn.disabled = true; btn.textContent = 'Procesando...';

  if (typeof OpenPay === 'undefined') {
    alertEl.textContent = 'El módulo de pagos no está disponible. Intenta recargar la página.';
    btn.disabled = false; btn.textContent = 'Confirmar y pagar';
    return;
  }

  // Priorizar el valor de retorno de setup(); como fallback leer el campo oculto
  const deviceSessionId = _openpayDeviceSessionId
    || document.getElementById('deviceSessionId').value;
  if (!deviceSessionId) {
    initOpenpay();
    alertEl.textContent = 'Preparando módulo de seguridad, intenta de nuevo en unos segundos.';
    btn.disabled = false; btn.textContent = 'Confirmar y pagar';
    return;
  }

  OpenPay.token.create({
    holder_name:       titular,
    card_number:       numero,
    expiration_month:  mes,
    expiration_year:   anio,
    cvv2:              cvv
  }, _onOpenpayTokenSuccess, _onOpenpayTokenError);
}

async function _onOpenpayTokenSuccess(response) {
  const tokenId         = response.data.id;
  const deviceSessionId = _openpayDeviceSessionId
    || document.getElementById('deviceSessionId').value;
  const paqueteId       = parseInt(document.getElementById('pendingPaqueteId').value);
  const alertEl         = document.getElementById('pagoAlert');

  try {
    const result = await api('POST', '/pagos/paquete', { paqueteId, tokenId, deviceSessionId });
    misCreditosCycling       = result.creditosCycling ?? 0;
    misCreditosCyclingVencen = result.creditosCyclingVencen ?? null;
    misCreditosPilates       = result.creditosPilates ?? 0;
    misCreditosPilatesVencen = result.creditosPilatesVencen ?? null;
    _actualizarCreditosBadge();

    const info   = _PAQUETES_INFO[paqueteId];
    const disc   = info?.disciplina === 'CYCLING' ? 'Indoor Cycling' : 'Pilates';
    const vencen = info?.disciplina === 'CYCLING' ? result.creditosCyclingVencen : result.creditosPilatesVencen;
    const vence  = _fmtFecha(vencen);

    document.getElementById('pagoConfirmPanel').style.display = 'none';
    document.getElementById('pagoSuccessPanel').innerHTML = `
      <div class="pago-success-icon">✓</div>
      <h3 class="pago-success-titulo">¡Pago exitoso!</h3>
      <p class="pago-success-detalle">
        Se agregaron <strong>${result.clasesAgregadas} clase${result.clasesAgregadas > 1 ? 's' : ''}</strong>
        de <strong>${disc}</strong> a tu cuenta.
        ${vence ? `<br>Válidas hasta el <strong>${vence}</strong>.` : ''}
      </p>
      <p class="pago-success-txn">ID de transacción: ${result.transaccionId}</p>`;
    document.getElementById('pagoSuccessPanel').style.display = 'block';
    document.getElementById('pagoSuccessActions').style.display = 'block';
  } catch(e) {
    alertEl.textContent = e.message;
    const btn = document.getElementById('pagoBtnConfirmar');
    btn.disabled = false; btn.textContent = 'Confirmar y pagar';
  }
}

function _onOpenpayTokenError(response) {
  const alertEl = document.getElementById('pagoAlert');
  const desc = response.data?.description || 'Error al procesar la tarjeta.';
  alertEl.textContent = desc;
  cancelarConfirmacionPago();
  const btn = document.getElementById('pagoBtnConfirmar');
  btn.disabled = false; btn.textContent = 'Confirmar y pagar';
}

function _showSinCreditos(disciplina) {
  const nombre = disciplina === 'cycling' ? 'Indoor Cycling' : disciplina === 'pilates' ? 'Pilates' : 'esta disciplina';
  showConfirm(
    `Sin clases de ${nombre}`,
    `No tienes clases de ${nombre} disponibles. Adquiere un paquete para continuar.`,
    'Ver paquetes',
    'btn-accent',
    () => { document.getElementById('paquetes').scrollIntoView({ behavior: 'smooth' }); }
  );
}

function formatCardNumber(input) {
  let val = input.value.replace(/\D/g, '').slice(0, 16);
  input.value = val.replace(/(.{4})/g, '$1 ').trim();
}

function closePago() {
  document.getElementById('pagoOverlay').classList.remove('show');
  document.getElementById('pagoConfirmPanel').style.display = 'none';
  document.getElementById('pagoSuccessPanel').style.display = 'none';
  document.getElementById('pagoSuccessActions').style.display = 'none';
  document.getElementById('pago-form').style.display = 'block';
  const btnConfirmar = document.getElementById('pagoBtnConfirmar');
  btnConfirmar.disabled = false;
  btnConfirmar.textContent = 'Confirmar y pagar';
}

function pagoOverlayClick(e) {
  if (e.target === document.getElementById('pagoOverlay')) closePago();
}

/* ═══════════════════════════════════════════
   COBRO EN EFECTIVO (Admin)
═══════════════════════════════════════════ */
let efClientesList = [];
let efSelectedClienteId = null;

function _renderEfPaquetes() {
  const container = document.getElementById('efPaquetes');
  if (!container) return;
  const cycling = [], pilates = [];
  Object.entries(_PAQUETES_INFO).forEach(([id, p]) => {
    (p.disciplina === 'CYCLING' ? cycling : pilates).push({ id, ...p });
  });
  if (!cycling.length && !pilates.length) {
    container.innerHTML = '<p style="color:var(--stone);font-size:13px;grid-column:1/-1">Cargando paquetes...</p>';
    return;
  }
  const fmt = p => `$${Number(p.precio).toLocaleString('es-MX')}`;
  const renderSection = (titulo, subtitulo, disc, paquetes) => `
    <div class="ef-disc-section ef-disc-${disc}">
      <div class="ef-disc-header">
        <div>
          <div class="ef-disc-label">${subtitulo}</div>
          <div class="ef-disc-titulo">${titulo}</div>
        </div>
        <span class="ef-disc-count">${paquetes.length} paquete${paquetes.length !== 1 ? 's' : ''}</span>
      </div>
      <div class="ef-disc-body">
        ${paquetes.map(p => {
          const label = p.esMensual ? `Mensual <span style="opacity:.6;font-size:11px">(${p.clases} clases)</span>` : `${p.clases} clase${p.clases > 1 ? 's' : ''}`;
          const vigencia = p.vigenciaDias === 15 ? '15 días' : '1 mes';
          return `<label class="ef-pkg">
            <input type="radio" name="efPkg" value="${p.id}"/>
            <span class="ef-pkg-label">${label}<span class="ef-pkg-vigencia">${vigencia}</span></span>
            <span class="ef-pkg-price">${fmt(p)}</span>
          </label>`;
        }).join('')}
      </div>
    </div>`;
  container.innerHTML =
    renderSection('Indoor Cycling', 'Alta intensidad · Cardio', 'cycling', cycling) +
    renderSection('Pilates Reformer', 'Control & fuerza · Flexibilidad', 'pilates', pilates);
}

async function openCobroEfectivo() {
  document.getElementById('efClienteId').value    = '';
  document.getElementById('efClienteSearch').value = '';
  document.getElementById('efCreditosInfo').textContent = '';
  document.getElementById('efAlert').textContent  = '';
  document.getElementById('efSsDropdown').innerHTML = '';
  document.getElementById('efSsDropdown').classList.remove('open');
  _renderEfPaquetes();
  efSelectedClienteId = null;

  document.getElementById('efectivoOverlay').classList.add('show');

  const input = document.getElementById('efClienteSearch');
  input.placeholder = 'Cargando clientes...';
  input.disabled = true;
  try {
    efClientesList = await api('GET', '/admin/usuarios');
    input.disabled = false;
    input.placeholder = 'Buscar por nombre o correo...';
    _initEfClienteSearch();
  } catch(_) {
    input.placeholder = 'Error al cargar clientes';
  }
}

function _initEfClienteSearch() {
  const input    = document.getElementById('efClienteSearch');
  const dropdown = document.getElementById('efSsDropdown');

  _renderEfOpciones(efClientesList);

  input.addEventListener('input', () => {
    efSelectedClienteId = null;
    document.getElementById('efClienteId').value = '';
    document.getElementById('efCreditosInfo').textContent = '';
    const q = input.value.trim().toLowerCase();
    const filtrados = q
      ? efClientesList.filter(c =>
          `${c.nombre} ${c.apellido}`.toLowerCase().includes(q) ||
          c.email.toLowerCase().includes(q))
      : efClientesList;
    _renderEfOpciones(filtrados);
    dropdown.classList.add('open');
  });

  input.addEventListener('focus', () => {
    _renderEfOpciones(efClientesList);
    dropdown.classList.add('open');
  });

  document.addEventListener('click', _closeEfDropdown);
}

function _renderEfOpciones(lista) {
  const dropdown = document.getElementById('efSsDropdown');
  dropdown.innerHTML = lista.slice(0, 8).map(c => `
    <div class="ss-option" onclick="selectEfCliente(${c.id},'${c.nombre} ${c.apellido}','${c.email}')">
      <span class="ss-name">${c.nombre} ${c.apellido}</span>
      <span class="ss-email">${c.email}</span>
    </div>`).join('') || '<div class="ss-option ss-empty">Sin resultados</div>';
}

async function selectEfCliente(id, nombre, email) {
  efSelectedClienteId = id;
  document.getElementById('efClienteId').value     = id;
  document.getElementById('efClienteSearch').value = `${nombre} — ${email}`;
  document.getElementById('efSsDropdown').classList.remove('open');

  const info = document.getElementById('efCreditosInfo');
  info.textContent = 'Consultando créditos...';
  try {
    const data = await api('GET', `/admin/creditos/${id}`);
    info.textContent = `Cycling: ${data.creditosCycling ?? 0} · Pilates: ${data.creditosPilates ?? 0}`;
  } catch(_) {
    info.textContent = '';
  }
}

function _closeEfDropdown(e) {
  const wrap = document.getElementById('efSsWrap');
  if (wrap && !wrap.contains(e.target))
    document.getElementById('efSsDropdown').classList.remove('open');
}

function confirmarCobroEfectivo() {
  const clienteId = document.getElementById('efClienteId').value;
  const paqueteId = parseInt(document.querySelector('input[name="efPkg"]:checked')?.value);
  const alertEl   = document.getElementById('efAlert');
  alertEl.textContent = '';

  if (!clienteId) { alertEl.textContent = 'Selecciona un cliente.'; return; }
  if (!paqueteId) { alertEl.textContent = 'Selecciona un paquete.'; return; }

  const pkgInfo = _PAQUETES_INFO[paqueteId];
  const pkgLabel = pkgInfo
    ? `${pkgInfo.esMensual ? `Mensual (${pkgInfo.clases} clases)` : `${pkgInfo.clases} clase${pkgInfo.clases > 1 ? 's' : ''}`} — $${Number(pkgInfo.precio).toLocaleString('es-MX')}`
    : paqueteId;
  const nombre = document.getElementById('efClienteSearch').value.split('—')[0].trim();

  showConfirm(
    'Confirmar cobro en efectivo',
    `¿Registrar pago de ${nombre} por ${pkgLabel}?`,
    'Confirmar cobro',
    'btn-accent',
    () => _execCobroEfectivo(parseInt(clienteId), paqueteId)
  );
}

async function _execCobroEfectivo(usuarioId, paqueteId) {
  try {
    const data = await api('POST', '/admin/creditos/efectivo', { usuarioId, paqueteId });
    const nombre = document.getElementById('efClienteSearch').value.split('—')[0].trim();
    closeEfectivo();
    showToast(
      'Cobro registrado',
      `Clases acreditadas a ${nombre}. Cycling: ${data.creditosCycling ?? 0} · Pilates: ${data.creditosPilates ?? 0}.`,
      'success'
    );
  } catch(e) {
    document.getElementById('efAlert').textContent = e.message;
  }
}

function closeEfectivo() {
  document.getElementById('efectivoOverlay').classList.remove('show');
  document.removeEventListener('click', _closeEfDropdown);
  efSelectedClienteId = null;
}

function efectivoOverlayClick(e) {
  if (e.target === document.getElementById('efectivoOverlay')) closeEfectivo();
}

/* ═══════════════════════════════════════════
   GESTIONAR PAQUETES (Admin)
═══════════════════════════════════════════ */
let paquetesAdminData   = [];
let editingPaqueteId    = null;

async function openGestionPaquetes() {
  document.getElementById('udrop')?.classList.remove('open');
  document.getElementById('paquetesOverlay').classList.add('show');
  closePaqueteForm();
  await _recargarPaquetes();
}

async function _recargarPaquetes() {
  const list = document.getElementById('gpaqList');
  list.innerHTML = '<div class="agenda-loading">Cargando paquetes...</div>';
  try {
    paquetesAdminData = await api('GET', '/admin/paquetes');
    _renderPaquetesAdmin();
  } catch(e) {
    list.innerHTML = '<div class="agenda-loading">No se pudieron cargar los paquetes.</div>';
  }
}

function _renderPaquetesAdmin() {
  const list = document.getElementById('gpaqList');
  if (!paquetesAdminData.length) {
    list.innerHTML = '<div class="agenda-loading">No hay paquetes registrados.</div>';
    return;
  }
  const cycling = paquetesAdminData.filter(p => p.disciplina === 'CYCLING');
  const pilates  = paquetesAdminData.filter(p => p.disciplina === 'PILATES');

  const fmtPrecio = p => `$${Number(p.precio).toLocaleString('es-MX')}`;
  const renderRow = p => `
    <div class="gpaq-row${p.activo ? '' : ' gpaq-row-inactive'}">
      <div class="gpaq-row-info">
        <span class="gpaq-row-nombre">${p.nombre}${p.esMensual ? ' <span class="gpaq-badge-mensual">Mensual</span>' : ''}</span>
        <span class="gpaq-row-meta">${p.numClases} clase${p.numClases > 1 ? 's' : ''} · ${fmtPrecio(p)} · ${p.vigenciaDias} días vigencia</span>
      </div>
      <div class="gpaq-row-actions">
        ${!p.activo ? '<span class="inst-badge-inactivo">Inactivo</span>' : ''}
        <button class="ap-btn" onclick="openPaqueteForm(${p.id})">Editar</button>
        <button class="ap-btn ${p.activo ? 'ap-btn-delete' : 'ap-btn-activate'}" onclick="togglePaqueteActivo(${p.id})">${p.activo ? 'Desactivar' : 'Activar'}</button>
        <button class="ap-btn ap-btn-delete" onclick="deletePaquete(${p.id})">Eliminar</button>
      </div>
    </div>`;

  const renderSection = (titulo, subtitulo, disc, items) => items.length === 0 ? '' : `
    <div class="gpaq-section gpaq-disc-${disc}">
      <div class="gpaq-section-header">
        <div>
          <div class="gpaq-section-label">${subtitulo}</div>
          <div class="gpaq-section-title">${titulo}</div>
        </div>
        <span class="gpaq-section-count">${items.length} paquete${items.length !== 1 ? 's' : ''}</span>
      </div>
      <div class="gpaq-section-body">
        ${items.map(renderRow).join('')}
      </div>
    </div>`;

  list.innerHTML =
    renderSection('Indoor Cycling', 'Alta intensidad · Cardio', 'cycling', cycling) +
    renderSection('Pilates Reformer', 'Control & fuerza · Flexibilidad', 'pilates', pilates);
}

function openPaqueteForm(id) {
  editingPaqueteId = id;
  const wrap = document.getElementById('gpaqFormWrap');
  document.getElementById('gpaqFormAlert').textContent = '';
  document.getElementById('gpaqFormAlert').classList.remove('show');

  if (id === null) {
    document.getElementById('gpaqFormTitle').textContent = 'Nuevo paquete';
    document.getElementById('gpaqNombre').value    = '';
    document.getElementById('gpaqDisciplina').value = 'CYCLING';
    document.getElementById('gpaqNumClases').value  = '';
    document.getElementById('gpaqPrecio').value     = '';
    document.getElementById('gpaqVigencia').value   = '30';
    document.getElementById('gpaqEsMensual').checked = false;
  } else {
    const p = paquetesAdminData.find(x => x.id === id);
    if (!p) return;
    document.getElementById('gpaqFormTitle').textContent = 'Editar paquete';
    document.getElementById('gpaqNombre').value     = p.nombre;
    document.getElementById('gpaqDisciplina').value  = p.disciplina;
    document.getElementById('gpaqNumClases').value   = p.numClases;
    document.getElementById('gpaqPrecio').value      = p.precio;
    document.getElementById('gpaqVigencia').value    = p.vigenciaDias;
    document.getElementById('gpaqEsMensual').checked = p.esMensual;
  }

  wrap.style.display = 'block';
  wrap.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function closePaqueteForm() {
  document.getElementById('gpaqFormWrap').style.display = 'none';
  editingPaqueteId = null;
}

function savePaquete() {
  const nombre     = document.getElementById('gpaqNombre').value.trim();
  const disciplina = document.getElementById('gpaqDisciplina').value;
  const numClases  = parseInt(document.getElementById('gpaqNumClases').value);
  const precio     = parseFloat(document.getElementById('gpaqPrecio').value);
  const vigencia   = parseInt(document.getElementById('gpaqVigencia').value);
  const esMensual  = document.getElementById('gpaqEsMensual').checked;
  const alertEl    = document.getElementById('gpaqFormAlert');

  alertEl.classList.remove('show');
  if (!nombre)            { alertEl.textContent = 'El nombre es obligatorio.';         alertEl.classList.add('show'); return; }
  if (!numClases || numClases < 1) { alertEl.textContent = 'Número de clases inválido.'; alertEl.classList.add('show'); return; }
  if (!precio || precio <= 0)      { alertEl.textContent = 'El precio debe ser mayor a 0.'; alertEl.classList.add('show'); return; }
  if (!vigencia || vigencia < 1)   { alertEl.textContent = 'La vigencia debe ser al menos 1 día.'; alertEl.classList.add('show'); return; }

  const accion = editingPaqueteId ? 'actualizar' : 'crear';
  showConfirm(
    editingPaqueteId ? 'Actualizar paquete' : 'Nuevo paquete',
    `¿Confirmas ${accion} el paquete "${nombre}"?`,
    'Confirmar',
    'btn-accent',
    () => _execSavePaquete({ nombre, disciplina, numClases, precio, vigenciaDias: vigencia, esMensual })
  );
}

async function _execSavePaquete(body) {
  const alertEl = document.getElementById('gpaqFormAlert');
  try {
    if (editingPaqueteId) {
      await api('PUT', `/admin/paquetes/${editingPaqueteId}`, body);
    } else {
      await api('POST', '/admin/paquetes', body);
    }
    closePaqueteForm();
    await _recargarPaquetes();
    await loadPaquetes();
    showToast('Paquete guardado', 'Los cambios se aplicaron correctamente.', 'success');
  } catch(e) {
    alertEl.textContent = e.message;
    alertEl.classList.add('show');
  }
}

async function togglePaqueteActivo(id) {
  const p = paquetesAdminData.find(x => x.id === id);
  if (!p) return;
  const accion = p.activo ? 'desactivar' : 'activar';
  showConfirm(
    `${p.activo ? 'Desactivar' : 'Activar'} paquete`,
    `¿Confirmas ${accion} "${p.nombre}"?${p.activo ? ' No aparecerá para los clientes.' : ''}`,
    'Confirmar',
    p.activo ? 'btn-accent' : '',
    async () => {
      try {
        await api('PATCH', `/admin/paquetes/${id}/toggle`);
        await _recargarPaquetes();
        await loadPaquetes();
        showToast('Paquete actualizado', `"${p.nombre}" ha sido ${p.activo ? 'desactivado' : 'activado'}.`, 'success');
      } catch(e) { showToast('Error', e.message); }
    }
  );
}

async function deletePaquete(id) {
  const p = paquetesAdminData.find(x => x.id === id);
  if (!p) return;
  showConfirm(
    'Eliminar paquete',
    `¿Eliminar permanentemente "${p.nombre}"? Esta acción no se puede deshacer.`,
    'Eliminar',
    'btn-accent',
    async () => {
      try {
        await api('DELETE', `/admin/paquetes/${id}`);
        await _recargarPaquetes();
        await loadPaquetes();
        showToast('Paquete eliminado', `"${p.nombre}" ha sido eliminado.`, 'success');
      } catch(e) { showToast('Error', e.message); }
    }
  );
}

function closePaquetes() {
  document.getElementById('paquetesOverlay').classList.remove('show');
  closePaqueteForm();
}

function paquetesOverlayClick(e) {
  if (e.target === document.getElementById('paquetesOverlay')) closePaquetes();
}

/* ═══════════════════════════════════════════
   EQUIPO DEL ESTUDIO (Admin)
═══════════════════════════════════════════ */
async function openEquipo() {
  document.getElementById('equipoAlert').textContent = '';
  document.getElementById('equipoList').innerHTML = '<p style="color:var(--stone);font-size:13px">Cargando...</p>';
  document.getElementById('equipoOverlay').classList.add('show');
  try {
    const lista = await api('GET', '/admin/equipo');
    _renderEquipoList(lista);
  } catch(e) {
    document.getElementById('equipoAlert').textContent = e.message;
  }
}

function _renderEquipoList(lista) {
  const nombres = { SPINNING: 'Indoor Cycling', PILATES: 'Pilates Reformer' };
  document.getElementById('equipoList').innerHTML = lista.map(e => `
    <div class="equipo-row">
      <div class="equipo-info">
        <span class="equipo-nombre">${e.nombre}</span>
        <span class="equipo-clases">${e.clasesAfectadas} clase${e.clasesAfectadas !== 1 ? 's' : ''} activas</span>
      </div>
      <div class="equipo-control">
        <label class="equipo-label">Cantidad</label>
        <input type="number" class="equipo-input" id="equipoQty_${e.tipoClase}" value="${e.cantidad}" min="1" max="100">
        <button class="equipo-btn" onclick="guardarEquipo('${e.tipoClase}')">Guardar</button>
      </div>
    </div>
  `).join('');
}

async function guardarEquipo(tipo) {
  const input = document.getElementById(`equipoQty_${tipo}`);
  const cantidad = parseInt(input.value);
  if (!cantidad || cantidad < 1) {
    document.getElementById('equipoAlert').textContent = 'La cantidad debe ser al menos 1.';
    return;
  }
  document.getElementById('equipoAlert').textContent = '';
  try {
    const result = await api('PUT', `/admin/equipo/${tipo}`, { cantidad });
    showToast('Equipo actualizado',
      `${result.nombre}: ${result.cantidad} unidades. ${result.clasesAfectadas} clase${result.clasesAfectadas !== 1 ? 's' : ''} actualizada${result.clasesAfectadas !== 1 ? 's' : ''}.`,
      'success');
    buildAgenda();
    loadCapacidad();
  } catch(e) {
    document.getElementById('equipoAlert').textContent = e.message;
  }
}

function closeEquipo() {
  document.getElementById('equipoOverlay').classList.remove('show');
}

function equipoOverlayClick(e) {
  if (e.target === document.getElementById('equipoOverlay')) closeEquipo();
}

/* ═══════════════════════════════════════════
   GESTIÓN INSTRUCTORES (Admin)
═══════════════════════════════════════════ */
let instructoresAdminData = [];
let editingInstructorId   = null;

async function openGestionInstructores() {
  document.getElementById('instructoresAdminOverlay').classList.add('show');
  const body = document.getElementById('instructoresAdminBody');
  body.innerHTML = '<div class="agenda-loading">Cargando instructores...</div>';
  try {
    instructoresAdminData = await api('GET', '/admin/instructores/todos');
    renderInstructoresAdmin();
  } catch(e) {
    body.innerHTML = '<div class="agenda-loading">No se pudieron cargar los instructores.</div>';
  }
}

function renderInstructoresAdmin() {
  const body = document.getElementById('instructoresAdminBody');
  if (!instructoresAdminData.length) {
    body.innerHTML = '<div class="agenda-loading">No hay instructores registrados.</div>';
    return;
  }
  body.innerHTML = instructoresAdminData.map(i => {
    const fotoEl = i.fotoUrl
      ? `<img src="${imgSrc(i.fotoUrl)}" class="inst-admin-avatar" alt="${i.nombre}">`
      : `<div class="inst-admin-avatar-ph">${i.nombre.charAt(0)}</div>`;
    const espLabel = i.especialidad === 'SPINNING' ? 'Indoor Cycling' : 'Pilates';
    return `
      <div class="inst-admin-card${i.activo ? '' : ' inst-inactive'}">
        <div class="inst-admin-foto">${fotoEl}</div>
        <div class="inst-admin-info">
          <div class="inst-admin-nombre">${i.nombre} ${i.apellido}</div>
          <div class="inst-admin-esp">${espLabel}</div>
          <div class="inst-admin-bio">${i.bio || ''}</div>
          ${!i.activo ? '<span class="inst-badge-inactivo">Inactivo</span>' : ''}
        </div>
        <div class="inst-admin-actions">
          <button class="ap-btn" onclick="openEditarInstructor(${i.id})">Editar</button>
          <button class="ap-btn ${i.activo ? 'ap-btn-delete' : 'ap-btn-activate'}" onclick="toggleInstructorActivo(${i.id})">${i.activo ? 'Desactivar' : 'Activar'}</button>
        </div>
      </div>`;
  }).join('');
}

function openNuevoInstructor() {
  editingInstructorId = null;
  document.getElementById('instFormTitle').textContent = 'Nuevo instructor';
  document.getElementById('instFormId').value = '';
  document.getElementById('instFormNombre').value = '';
  document.getElementById('instFormApellido').value = '';
  document.getElementById('instFormEsp').value = 'SPINNING';
  document.getElementById('instFormBio').value = '';
  document.getElementById('instFormFoto').value = '';
  document.getElementById('instFotoImg').style.display = 'none';
  document.getElementById('instFotoImg').src = '';
  document.getElementById('instFotoInitial').textContent = '?';
  document.getElementById('instFormAlert').textContent = '';
  document.getElementById('instructorFormOverlay').classList.add('show');
}

function openEditarInstructor(id) {
  const inst = instructoresAdminData.find(i => i.id === id);
  if (!inst) return;
  editingInstructorId = id;
  document.getElementById('instFormTitle').textContent = 'Editar instructor';
  document.getElementById('instFormId').value = id;
  document.getElementById('instFormNombre').value = inst.nombre;
  document.getElementById('instFormApellido').value = inst.apellido;
  document.getElementById('instFormEsp').value = inst.especialidad;
  document.getElementById('instFormBio').value = inst.bio || '';
  document.getElementById('instFormFoto').value = '';
  document.getElementById('instFormAlert').textContent = '';
  const img = document.getElementById('instFotoImg');
  const ini = document.getElementById('instFotoInitial');
  if (inst.fotoUrl) {
    img.src = imgSrc(inst.fotoUrl);
    img.style.display = 'block';
    ini.style.display = 'none';
  } else {
    img.src = '';
    img.style.display = 'none';
    ini.textContent = inst.nombre.charAt(0);
    ini.style.display = '';
  }
  document.getElementById('instructorFormOverlay').classList.add('show');
}

function previewFoto(input) {
  if (!input.files || !input.files[0]) return;
  const reader = new FileReader();
  reader.onload = e => {
    const img = document.getElementById('instFotoImg');
    img.src = e.target.result;
    img.style.display = 'block';
    document.getElementById('instFotoInitial').style.display = 'none';
  };
  reader.readAsDataURL(input.files[0]);
}

function guardarInstructor() {
  const nombre = document.getElementById('instFormNombre').value.trim();
  if (!nombre) {
    document.getElementById('instFormAlert').textContent = 'El nombre es obligatorio.';
    return;
  }
  showConfirm(
    editingInstructorId ? 'Actualizar instructor' : 'Nuevo instructor',
    `¿Confirmas ${editingInstructorId ? 'actualizar' : 'crear'} al instructor ${nombre}?`,
    'Confirmar',
    'btn-accent',
    _execGuardarInstructor
  );
}

async function _execGuardarInstructor() {
  const nombre       = document.getElementById('instFormNombre').value.trim();
  const apellido     = document.getElementById('instFormApellido').value.trim();
  const especialidad = document.getElementById('instFormEsp').value;
  const bio          = document.getElementById('instFormBio').value.trim();
  const fotoInput    = document.getElementById('instFormFoto');

  try {
    let instructor;
    const body = { nombre, apellido, especialidad, bio };
    if (editingInstructorId) {
      instructor = await api('PUT', `/admin/instructores/${editingInstructorId}`, body);
    } else {
      instructor = await api('POST', '/admin/instructores', body);
    }

    if (fotoInput.files && fotoInput.files[0]) {
      const fd = new FormData();
      fd.append('foto', fotoInput.files[0]);
      const token = getToken();
      const res = await fetch(`${BASE_URL}/admin/instructores/${instructor.id}/foto`, {
        method: 'POST',
        headers: token ? { 'Authorization': `Bearer ${token}` } : {},
        body: fd
      });
      if (res.ok) instructor = await res.json();
    }

    const idx = instructoresAdminData.findIndex(i => i.id === instructor.id);
    if (idx >= 0) instructoresAdminData[idx] = instructor;
    else instructoresAdminData.push(instructor);

    renderInstructoresAdmin();
    loadInstructores();
    closeInstructorForm();
    showToast('Instructor guardado', `${instructor.nombre} ${instructor.apellido} actualizado.`, '');
  } catch(e) {
    document.getElementById('instFormAlert').textContent = e.message;
  }
}

function toggleInstructorActivo(id) {
  const inst = instructoresAdminData.find(i => i.id === id);
  if (!inst) return;
  const accion = inst.activo ? 'desactivar' : 'activar';
  showConfirm(
    inst.activo ? 'Desactivar instructor' : 'Activar instructor',
    `¿Seguro que deseas ${accion} a ${inst.nombre} ${inst.apellido}?`,
    inst.activo ? 'Desactivar' : 'Activar',
    inst.activo ? 'btn-danger' : 'btn-accent',
    () => _execToggleInstructorActivo(id)
  );
}

async function _execToggleInstructorActivo(id) {
  try {
    const updated = await api('PATCH', `/admin/instructores/${id}/toggle`);
    const idx = instructoresAdminData.findIndex(i => i.id === id);
    if (idx >= 0) instructoresAdminData[idx] = updated;
    renderInstructoresAdmin();
    loadInstructores();
    showToast(
      updated.activo ? 'Instructor activado' : 'Instructor desactivado',
      `${updated.nombre} ${updated.apellido}.`, ''
    );
  } catch(e) {
    showToast('Error', e.message);
  }
}

function closeGestionInstructores() {
  document.getElementById('instructoresAdminOverlay').classList.remove('show');
}

function closeInstructorForm() {
  document.getElementById('instructorFormOverlay').classList.remove('show');
}

function instAdminOverlayClick(e) {
  if (e.target === document.getElementById('instructoresAdminOverlay')) closeGestionInstructores();
}

function instFormOverlayClick(e) {
  if (e.target === document.getElementById('instructorFormOverlay')) closeInstructorForm();
}

/* ═══════════════════════════════════════════
   TOAST
═══════════════════════════════════════════ */
let toastTimer;
function showToast(title, msg = '', type = '') {
  const t = document.getElementById('toast');
  document.getElementById('toastTitle').textContent = title;
  document.getElementById('toastMsg').textContent   = msg;
  t.className = 'toast show' + (type ? ' ' + type : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3800);
}

/* ═══════════════════════════════════════════
   LUGARES EN CURSO (Admin)
═══════════════════════════════════════════ */
let enCursoData = [];
let enCursoDisc = 'CYCLING';
let _enCursoTimer = null;

async function openEnCurso() {
  document.getElementById('udrop')?.classList.remove('open');
  enCursoDisc = 'CYCLING';
  document.getElementById('ecPillCycling').classList.add('active');
  document.getElementById('ecPillPilates').classList.remove('active');
  document.getElementById('enCursoOverlay').classList.add('show');
  await _recargarEnCurso();
  _enCursoTimer = setInterval(_recargarEnCurso, 60_000);
}

async function _recargarEnCurso() {
  document.getElementById('ecBody').innerHTML = '<p class="resv-empty">Cargando...</p>';
  try {
    enCursoData = await api('GET', '/admin/clases/en-curso');
    document.getElementById('ecTimestamp').textContent =
      `Actualizado: ${new Date().toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })}`;
    _renderEnCurso();
  } catch(e) {
    document.getElementById('ecBody').innerHTML = `<p class="resv-empty">${e.message}</p>`;
  }
}

function setEnCursoDisc(disc) {
  enCursoDisc = disc;
  document.getElementById('ecPillCycling').classList.toggle('active', disc === 'CYCLING');
  document.getElementById('ecPillPilates').classList.toggle('active', disc === 'PILATES');
  _renderEnCurso();
}

function _renderEnCurso() {
  const body   = document.getElementById('ecBody');
  const clases = enCursoData.filter(c => c.disciplina === enCursoDisc);
  const label  = enCursoDisc === 'CYCLING' ? 'Indoor Cycling' : 'Pilates';

  if (!clases.length) {
    body.innerHTML = `<p class="resv-empty">No hay clases de ${label} en curso en este momento.</p>`;
    return;
  }

  body.innerHTML = clases.map(c => {
    const libres   = c.cupoTotal - c.cupoTomado;
    const instStr  = c.instructor ? ` · <span style="font-weight:400;color:var(--stone)">${c.instructor}</span>` : '';
    const cupoStr  = `${c.cupoTomado}/${c.cupoTotal} ocupados · ${libres} libre${libres !== 1 ? 's' : ''}`;

    const filas = c.reservaciones.length === 0
      ? '<p class="resv-empty" style="font-size:13px;margin:14px 0 6px">Sin reservaciones confirmadas.</p>'
      : c.reservaciones.map(r => {
          const lugar = r.lugarNumero ?? '—';
          return `
            <div class="ec-item" id="ec-r-${r.reservacionId}">
              <div class="ec-lugar">${lugar}</div>
              <div class="ec-info">
                <div class="ec-nombre">${r.nombre}</div>
                <div class="ec-email">${r.email}</div>
              </div>
              <button class="ec-liberar-btn" onclick="liberarLugar(${r.reservacionId},'${r.nombre.replace(/'/g,"\\'")}')">Liberar</button>
            </div>`;
        }).join('');

    return `
      <div class="ec-clase-card">
        <div class="ec-clase-header">
          <span class="ec-clase-hora">Clase ${c.hora.replace(/^0/, '')}${instStr}</span>
          <span class="ec-cupo-badge">${cupoStr}</span>
        </div>
        <div class="ec-lista">${filas}</div>
      </div>`;
  }).join('');
}

function liberarLugar(reservacionId, nombre) {
  showConfirm(
    'Liberar lugar',
    `¿Confirmar que ${nombre} no asistió? El lugar queda disponible y el crédito no se devuelve.`,
    'Sí, liberar lugar',
    'btn-danger',
    () => doLiberarLugar(reservacionId)
  );
}

async function doLiberarLugar(reservacionId) {
  try {
    await api('PATCH', `/admin/reservaciones/${reservacionId}/no-asistio`);
    enCursoData = enCursoData.map(c => ({
      ...c,
      cupoTomado: c.reservaciones.some(r => r.reservacionId === reservacionId)
        ? c.cupoTomado - 1 : c.cupoTomado,
      reservaciones: c.reservaciones.filter(r => r.reservacionId !== reservacionId)
    }));
    _renderEnCurso();
    showToast('Lugar liberado', 'El lugar fue liberado. El crédito no fue devuelto al cliente.');
  } catch(e) {
    showToast('Error', e.message);
  }
}

function closeEnCurso() {
  document.getElementById('enCursoOverlay').classList.remove('show');
  clearInterval(_enCursoTimer);
  _enCursoTimer = null;
}
function enCursoOverlayClick(e) { if (e.target === document.getElementById('enCursoOverlay')) closeEnCurso(); }

/* ═══════════════════════════════════════════
   CLIENTES Y CRÉDITOS (Admin)
═══════════════════════════════════════════ */
let _clientesData = [];

async function openClientes() {
  document.getElementById('clientesOverlay').classList.add('show');
  document.getElementById('clientesBuscar').value = '';
  document.getElementById('clientesBody').innerHTML = '<p style="text-align:center;padding:32px;color:var(--stone)">Cargando…</p>';
  try {
    _clientesData = await api('GET', '/admin/usuarios/creditos');
    _renderClientes(_clientesData);
  } catch(e) {
    document.getElementById('clientesBody').innerHTML = `<p style="text-align:center;padding:24px;color:var(--danger)">${e.message}</p>`;
  }
}

function _filtrarClientes() {
  const q = document.getElementById('clientesBuscar').value.toLowerCase();
  const filtrados = q
    ? _clientesData.filter(c =>
        (c.nombre + ' ' + c.apellido).toLowerCase().includes(q) ||
        c.email.toLowerCase().includes(q))
    : _clientesData;
  _renderClientes(filtrados);
}

function _renderClientes(lista) {
  const body = document.getElementById('clientesBody');
  const footer = document.getElementById('clientesFooter');
  if (!lista.length) {
    body.innerHTML = '<p style="text-align:center;padding:32px;color:var(--stone)">Sin resultados.</p>';
    footer.textContent = '';
    return;
  }
  const hoy = new Date();
  const fmtFecha = iso => {
    if (!iso) return '—';
    const [y,m,d] = iso.split('-');
    return `${d}/${m}/${y}`;
  };
  const creditoCell = (n, vence) => {
    if (n === 0) return '<span style="color:var(--stone-light)">—</span>';
    const vStr = fmtFecha(vence);
    const vDate = vence ? new Date(vence) : null;
    const vencido = vDate && vDate < hoy;
    const color = vencido ? 'var(--danger)' : 'var(--success)';
    return `<strong style="color:${color}">${n}</strong> <span class="rpt-vence" style="color:${vencido?'var(--danger)':'var(--stone)'}">vence ${vStr}</span>`;
  };
  body.innerHTML = `
    <table class="rpt-table">
      <thead><tr>
        <th>Nombre</th>
        <th>Email</th>
        <th>Teléfono</th>
        <th>Cycling</th>
        <th>Pilates</th>
      </tr></thead>
      <tbody>
        ${lista.map(c => `
          <tr>
            <td>${c.nombre} ${c.apellido}</td>
            <td class="rpt-mono">${c.email}</td>
            <td>${c.telefono || '—'}</td>
            <td>${creditoCell(c.creditosCycling, c.creditosCyclingVencen)}</td>
            <td>${creditoCell(c.creditosPilates, c.creditosPilatesVencen)}</td>
          </tr>`).join('')}
      </tbody>
    </table>`;
  footer.textContent = `${lista.length} cliente${lista.length !== 1 ? 's' : ''}`;
}

function closeClientes() { document.getElementById('clientesOverlay').classList.remove('show'); }
function clientesOverlayClick(e) { if (e.target === document.getElementById('clientesOverlay')) closeClientes(); }

/* ═══════════════════════════════════════════
   HISTORIAL DE VENTAS (Admin)
═══════════════════════════════════════════ */
let _historialPeriodo = 'dia';

async function openHistorial() {
  document.getElementById('historialOverlay').classList.add('show');
  _historialPeriodo = 'dia';
  document.querySelectorAll('#historialPills .ec-pill').forEach((b,i) => b.classList.toggle('active', i === 0));
  await _cargarHistorial();
}

function setHistorialPeriodo(periodo, btn) {
  _historialPeriodo = periodo;
  document.querySelectorAll('#historialPills .ec-pill').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  _cargarHistorial();
}

async function _cargarHistorial() {
  document.getElementById('historialBody').innerHTML = '<p style="text-align:center;padding:32px;color:var(--stone)">Cargando…</p>';
  document.getElementById('historialResumen').innerHTML = '';
  try {
    const data = await api('GET', `/admin/pagos/historial?periodo=${_historialPeriodo}`);
    _renderHistorial(data);
  } catch(e) {
    document.getElementById('historialBody').innerHTML = `<p style="text-align:center;padding:24px;color:var(--danger)">${e.message}</p>`;
  }
}

function _renderHistorial(data) {
  const { historial, totalCobrado, totalClases, totalTransacciones } = data;

  const enLinea   = historial.filter(p => p.metodo === 'TARJETA');
  const enEfectivo = historial.filter(p => p.metodo === 'EFECTIVO');

  const sumaLinea    = enLinea.reduce((s, p) => s + p.monto, 0);
  const sumaEfectivo = enEfectivo.reduce((s, p) => s + p.monto, 0);
  const clasesLinea    = enLinea.reduce((s, p) => s + p.clasesAgregadas, 0);
  const clasesEfectivo = enEfectivo.reduce((s, p) => s + p.clasesAgregadas, 0);

  const fmt = n => `$${Number(n).toLocaleString('es-MX', {minimumFractionDigits:2, maximumFractionDigits:2})}`;

  document.getElementById('historialResumen').innerHTML = `
    <div class="rpt-kpi-row">
      <div class="rpt-kpi rpt-kpi-total">
        <div class="rpt-kpi-val">${fmt(totalCobrado)}</div>
        <div class="rpt-kpi-lbl">Total cobrado MXN</div>
      </div>
      <div class="rpt-kpi rpt-kpi-total">
        <div class="rpt-kpi-val">${totalTransacciones}</div>
        <div class="rpt-kpi-lbl">Transacciones</div>
      </div>
      <div class="rpt-kpi rpt-kpi-total">
        <div class="rpt-kpi-val">${totalClases}</div>
        <div class="rpt-kpi-lbl">Clases vendidas</div>
      </div>
    </div>
    <div class="rpt-metodo-row">
      <div class="rpt-metodo-card">
        <div class="rpt-metodo-header"><span class="rpt-tag rpt-tag-card">Tarjeta (en línea)</span></div>
        <div class="rpt-metodo-stat">${fmt(sumaLinea)} <span class="rpt-metodo-sub">${enLinea.length} venta${enLinea.length !== 1 ? 's' : ''} · ${clasesLinea} clase${clasesLinea !== 1 ? 's' : ''}</span></div>
      </div>
      <div class="rpt-metodo-card">
        <div class="rpt-metodo-header"><span class="rpt-tag rpt-tag-cash">Efectivo</span></div>
        <div class="rpt-metodo-stat">${fmt(sumaEfectivo)} <span class="rpt-metodo-sub">${enEfectivo.length} venta${enEfectivo.length !== 1 ? 's' : ''} · ${clasesEfectivo} clase${clasesEfectivo !== 1 ? 's' : ''}</span></div>
      </div>
    </div>`;

  const body = document.getElementById('historialBody');
  if (!historial.length) {
    body.innerHTML = '<p style="text-align:center;padding:32px;color:var(--stone)">Sin ventas en este período.</p>';
    return;
  }

  const discTag = d => d === 'CYCLING'
    ? '<span class="rpt-tag rpt-tag-cyc">Cycling</span>'
    : '<span class="rpt-tag rpt-tag-pil">Pilates</span>';

  const buildTable = (lista) => {
    if (!lista.length) return '<p style="padding:12px 0;color:var(--stone-light);font-size:13px">Sin registros en este período.</p>';
    return `
      <table class="rpt-table">
        <thead><tr>
          <th>Fecha</th>
          <th>Cliente</th>
          <th>Paquete</th>
          <th>Disciplina</th>
          <th style="text-align:right">Monto</th>
        </tr></thead>
        <tbody>
          ${lista.map(p => `
            <tr>
              <td class="rpt-mono" style="white-space:nowrap">${p.fechaPago}</td>
              <td>
                <div>${p.usuarioNombre}</div>
                <div style="font-size:11px;color:var(--stone)">${p.usuarioEmail}</div>
              </td>
              <td>${p.paqueteNombre}<br><span style="font-size:11px;color:var(--stone)">${p.clasesAgregadas} clase${p.clasesAgregadas !== 1 ? 's' : ''}</span></td>
              <td>${discTag(p.disciplina)}</td>
              <td style="text-align:right;font-weight:600">$${Number(p.monto).toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
            </tr>`).join('')}
        </tbody>
      </table>`;
  };

  body.innerHTML = `
    <div class="rpt-seccion">
      <div class="rpt-seccion-header">
        <span class="rpt-tag rpt-tag-card" style="font-size:12px;padding:3px 10px">Tarjeta (en línea)</span>
      </div>
      ${buildTable(enLinea)}
    </div>
    <div class="rpt-seccion">
      <div class="rpt-seccion-header">
        <span class="rpt-tag rpt-tag-cash" style="font-size:12px;padding:3px 10px">Efectivo</span>
      </div>
      ${buildTable(enEfectivo)}
    </div>`;
}

function closeHistorial() { document.getElementById('historialOverlay').classList.remove('show'); }
function historialOverlayClick(e) { if (e.target === document.getElementById('historialOverlay')) closeHistorial(); }

/* ═══════════════════════════════════════════
   AVISO DE PRIVACIDAD / TÉRMINOS / CONTACTO
═══════════════════════════════════════════ */
function openPrivacidad() { document.getElementById('privacidadOverlay').classList.add('show'); }
function closePrivacidad() { document.getElementById('privacidadOverlay').classList.remove('show'); }

function openTerminos() { document.getElementById('terminosOverlay').classList.add('show'); }
function closeTerminos() { document.getElementById('terminosOverlay').classList.remove('show'); }

function openContacto() { document.getElementById('contactoOverlay').classList.add('show'); }
function closeContacto() { document.getElementById('contactoOverlay').classList.remove('show'); }

/* ═══════════════════════════════════════════
   UTILS
═══════════════════════════════════════════ */
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') { closeAuth(); closeDrawer(); closeReserv(); closeConfirm(); closeClases(); closeClaseForm(); closeGenericConfirm(); closeSeatSelector(); closeGestionInstructores(); closeInstructorForm(); closePago(); closeEfectivo(); closeEquipo(); closePaquetes(); closeEnCurso(); closeClientes(); closeHistorial(); closePrivacidad(); closeTerminos(); closeContacto(); }
});

/* ═══════════════════════════════════════════
   INIT
═══════════════════════════════════════════ */
async function loadCapacidad() {
  try {
    const data = await api('GET', '/clases/equipo');
    data.forEach(e => {
      if (e.tipoClase === 'SPINNING') {
        document.getElementById('capacidadCycling').textContent = `${e.cantidad} bicicletas`;
      } else if (e.tipoClase === 'PILATES') {
        document.getElementById('capacidadPilates').textContent = `${e.cantidad} reformers`;
      }
    });
  } catch(_) {}
}

(function init() {
  const saved = getSavedUser();
  if (saved && getToken()) {
    currentUser = saved;
    document.getElementById('navActions').innerHTML = buildNavUser(saved);
    updateDrawerAuth(saved);
  }
  buildAgenda();
  loadInstructores();
  loadCreditos();
  loadPaquetes();
  loadCapacidad();

  const urlToken = new URLSearchParams(window.location.search).get('token');
  if (urlToken) {
    resetToken = urlToken;
    history.replaceState({}, '', window.location.pathname);
    openAuth('nuevaPassword');
  }
})();

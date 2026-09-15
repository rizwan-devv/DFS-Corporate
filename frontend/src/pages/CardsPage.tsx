import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type UiCard = {
  id: string;
  holder: string;
  last4: string;
  accountNo: string;
  network: string;
  status: string;
  product: string;
  gradient: string;
  expiry: string;
  relationshipNum?: string;
  raw?: unknown;
};

const DEMO: UiCard[] = [
  {
    id: 'CARD-1001',
    holder: 'DFS Corporate Merchant',
    last4: '4821',
    accountNo: '1002••••9012',
    network: 'DFS Pay',
    status: 'Active',
    product: 'Corporate Prepaid',
    gradient: 'card-grad-1',
    expiry: '09/28',
    relationshipNum: '10029012',
  },
];

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' ? (v as Record<string, unknown>) : {};
}

function pick(obj: Record<string, unknown>, keys: string[]): string {
  for (const k of keys) {
    const v = obj[k];
    if (v != null && String(v).trim()) return String(v);
  }
  return '';
}

function formatExpiry(raw: string): string {
  if (!raw) return '—';
  const s = raw.trim();
  if (/^\d{2}\/\d{2}$/.test(s)) return s;
  if (s.includes('T') || /^\d{4}-\d{2}-\d{2}/.test(s)) {
    const d = new Date(s);
    if (!Number.isNaN(d.getTime())) {
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const yy = String(d.getFullYear()).slice(-2);
      return `${mm}/${yy}`;
    }
  }
  return s.length > 12 ? s.slice(0, 10) : s;
}

function mapStatus(raw: string): string {
  if (!raw) return 'UNKNOWN';
  const u = raw.toUpperCase();
  if (u.includes('ACTIVE') || raw === '001' || raw === '1') return 'Active';
  if (u.includes('INACTIVE') || raw === '002' || raw === '2') return 'Inactive';
  if (u.includes('BLOCK') || u.includes('HOT') || raw === '003' || raw === '3') return 'Blocked';
  if (u.includes('PEND') || raw === '004' || raw === '4') return 'Pending';
  if (u.includes('COLD')) return 'Cold';
  return raw;
}

/** Prefer backend-normalized `items`; else dig into cms payload. */
function extractItems(payload: unknown): Record<string, unknown>[] {
  const root = asRecord(payload);
  if (Array.isArray(root.items)) return root.items.map(asRecord);
  if (root.item) return [asRecord(root.item)];

  const cms = asRecord(root.cms ?? root);
  const data = cms.data ?? cms.responseBody ?? cms.result ?? cms.content ?? cms;
  if (Array.isArray(data)) return data.map(asRecord);
  const d = asRecord(data);
  for (const key of ['items', 'content', 'cards', 'records', 'list', 'cardList']) {
    const arr = d[key];
    if (Array.isArray(arr)) return arr.map(asRecord);
  }
  if (d.cardId || d.id || d.accountNumber || d.pan || d.PAN || d.Title || d.relationshipNum) return [d];
  return [];
}

function mapCard(raw: Record<string, unknown>, index: number): UiCard {
  const pan = pick(raw, [
    'maskedPan', 'pan', 'PAN', 'cardNumber', 'cardNo', 'cardPan', 'maskedCardNumber', 'card_number',
  ]);
  const last4 =
    pick(raw, ['last4', 'lastFour', 'last_four']) ||
    (pan.replace(/\D/g, '').slice(-4) || '••••');
  const account = pick(raw, [
    'accountNumber', 'accountNo', 'accountNumberMasked', 'relationshipNum', 'relationshipNumber', 'Relationship',
  ]);
  const status = mapStatus(
    pick(raw, ['status', 'cardStatusName', 'statusName', 'cardStatusCode', 'statusCode', 'cardStatus', 'Status']),
  );
  const expiry = formatExpiry(
    pick(raw, ['expiry', 'expiryDate', 'cardExpiry', 'expireDate', 'expiryDateTime', 'validThru']),
  );
  const product =
    pick(raw, ['productName', 'productCode', 'product', 'cardType', 'cardTypeName', 'Product']) || 'Card';
  const network =
    pick(raw, ['network', 'scheme', 'brand', 'cardBrand']) ||
    (product.toUpperCase().includes('MASTER')
      ? 'Mastercard'
      : product.toUpperCase().includes('VISA')
        ? 'Visa'
        : 'DFS Pay');

  return {
    id: pick(raw, ['cardId', 'id']) || (pan ? `PAN-${last4}` : `CMS-${index + 1}`),
    holder:
      pick(raw, [
        'holderName', 'cardHolder', 'cardHolderName', 'customerName', 'embossedName', 'name',
        'cardTitle', 'CardTitle', 'accountTitle', 'title', 'Title',
      ]) || '—',
    last4,
    accountNo: account || '••••',
    network,
    status,
    product,
    gradient: `card-grad-${(index % 3) + 1}`,
    expiry,
    relationshipNum: pick(raw, ['relationshipNum', 'relationshipNumber', 'Relationship', 'accountNumber']) || account,
    raw,
  };
}

export function CardsPage() {
  const { session } = useAuth();
  const [cards, setCards] = useState<UiCard[]>([]);
  const [active, setActive] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(false);
  const [source, setSource] = useState<'demo' | 'cms' | 'empty'>('empty');
  const [error, setError] = useState<string | null>(null);
  const [cmsEnabled, setCmsEnabled] = useState(false);
  const [appConfigured, setAppConfigured] = useState(false);
  const [loadMode, setLoadMode] = useState<string>('');
  const [scopeKeys, setScopeKeys] = useState<string[]>([]);
  const [siblings, setSiblings] = useState<UiCard[]>([]);
  const [inquiryNote, setInquiryNote] = useState<string | null>(null);
  const [pin, setPin] = useState('');

  const card = cards[active] ?? cards[0];

  const load = useCallback(async () => {
    if (!session?.token) {
      setCards(DEMO);
      setSource('demo');
      setError('Login to load cards for your account.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const status = await api<{
        enabled?: boolean;
        appConfigured?: boolean;
        appReady?: boolean;
        portalReady?: boolean;
        mode?: string;
      }>('/api/cms/cards/status', { token: session.token });
      setCmsEnabled(!!status.enabled);
      setAppConfigured(!!status.appConfigured);
      if (!status.enabled) {
        setCards(DEMO);
        setSource('demo');
        setError('CMS disabled on server (DFS_CMS_API_ENABLED).');
        return;
      }
      const res = await api<{
        items?: unknown[];
        total?: number;
        scopeKeys?: string[];
        message?: string;
        scoped?: boolean;
        mode?: string;
      }>('/api/cms/cards/search', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ page: 0, size: 50, sort: 'createdOn', sortDir: 'desc' }),
      });
      setScopeKeys(Array.isArray(res.scopeKeys) ? res.scopeKeys.map(String) : []);
      setLoadMode(res.mode || status.mode || '');
      const items = extractItems(res).map(mapCard);
      if (items.length === 0) {
        setCards([]);
        setSource('empty');
        setError(
          res.message ||
            (!status.appConfigured
              ? 'Set DFS_CMS_APP_API_KEY / USERNAME / PASSWORD (same as AgentApp) for /card/inquiry.'
              : 'No cards matched this corporate account (dfsAccountId = CMS relationship number).'),
        );
      } else {
        setCards(items);
        setSource('cms');
        setActive(0);
        setError(null);
      }
    } catch (e) {
      setCards([]);
      setSource('empty');
      setError(e instanceof Error ? e.message : 'CMS unavailable');
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setFlipped(false);
    setInquiryNote(null);
  }, [active]);

  useEffect(() => {
    if (cards.length <= 1) return undefined;
    const id = window.setInterval(() => setActive((i) => (i + 1) % cards.length), 5600);
    return () => window.clearInterval(id);
  }, [cards.length]);

  useEffect(() => {
    if (!card) return;
    const same = cards.filter((c) => c.accountNo === card.accountNo && c.id !== card.id);
    setSiblings(same);
  }, [card, cards]);

  const statusChip = useMemo(() => {
    const s = (card?.status || '').toUpperCase();
    if (s.includes('ACTIVE')) return 'chip-success';
    if (s.includes('BLOCK') || s.includes('HOT') || s.includes('INACTIVE')) return 'chip-danger';
    return 'chip-warn';
  }, [card?.status]);

  async function loadDetail(cardId: string) {
    if (!session?.token || source !== 'cms') return;
    try {
      const res = await api<{
        item?: Record<string, unknown>;
        sameAccountDifferentCard?: boolean;
        sameAccountCards?: unknown[];
      }>(`/api/cms/cards/${encodeURIComponent(cardId)}`, { token: session.token });
      if (res.item) {
        const mapped = mapCard(res.item, 0);
        setCards((prev) => prev.map((c) => (c.id === cardId ? { ...mapped, gradient: c.gradient } : c)));
      }
      if (Array.isArray(res.sameAccountCards)) {
        setSiblings(res.sameAccountCards.map((x, i) => mapCard(asRecord(x), i)));
      }
    } catch {
      /* keep list data */
    }
  }

  async function runInquiry(unmask: boolean) {
    if (!session?.token || !card?.relationshipNum) {
      setInquiryNote('Login + relationship/account number required for CMS inquiry.');
      return;
    }
    try {
      setInquiryNote(unmask ? 'Requesting unmask…' : 'Loading masked inquiry…');
      await api('/api/cms/cards/inquiry', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          relationshipNum: card.relationshipNum,
          ...(unmask && pin ? { pin } : {}),
        }),
      });
      setInquiryNote(unmask ? 'Unmask response received.' : 'Masked inquiry OK.');
    } catch (e) {
      setInquiryNote(e instanceof Error ? e.message : 'Inquiry failed');
    }
  }

  const sourceLabel =
    source === 'cms'
      ? loadMode.includes('inquiry')
        ? 'CMS App inquiry'
        : loadMode.includes('portal')
          ? 'CMS Portal search'
          : 'CMS API'
      : source === 'demo'
        ? 'Demo'
        : 'No cards';

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Cards · CMS App"
        title="Card details"
        subtitle="Loaded like AgentApp: CMS App /card/inquiry by your party relationship number (dfsAccountId)."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        }
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Inquiry', title: 'By relationship', body: 'Same CMS App path AgentApp uses: relationshipNum → /card/inquiry.' },
          { accent: 'Mask', title: 'Sensitive by default', body: 'PAN and CVV stay hidden until audited unmask.' },
          {
            accent: 'Live',
            title: appConfigured ? 'CMS App ready' : cmsEnabled ? 'App creds missing' : 'CMS off',
            body: appConfigured
              ? 'Inquiry credentials configured.'
              : cmsEnabled
                ? 'Set DFS_CMS_APP_API_KEY / USERNAME / PASSWORD.'
                : 'Set DFS_CMS_API_ENABLED to go live.',
          },
        ]}
      />

      {error && <p className="api-banner">{error}</p>}
      <p className="muted" style={{ margin: 0 }}>
        Source: <strong>{sourceLabel}</strong>
        {cmsEnabled ? ' · integration enabled' : ' · integration disabled'}
        {appConfigured ? ' · App inquiry ready' : cmsEnabled ? ' · App inquiry not configured' : ''}
        {scopeKeys.length > 0 && (
          <>
            {' '}
            · scope <span className="mono">{scopeKeys.join(', ')}</span>
          </>
        )}
      </p>

      {!card ? (
        <section className="glass-panel animate-in">
          <h2 className="panel-title">No cards for this login</h2>
          <p className="muted">
            Set party <span className="mono">dfsAccountId</span> to the 13-digit CMS{' '}
            <strong>Relationship #</strong> (same value AgentApp uses), and configure{' '}
            <span className="mono">DFS_CMS_APP_*</span> on the server.
          </p>
        </section>
      ) : (
        <>
          <div className="cards-layout animate-in animate-in-delay-1">
            <section className="card-stage">
              <button
                type="button"
                className={`plastic-card ${card.gradient} ${flipped ? 'is-flipped' : ''}`}
                onClick={() => setFlipped((f) => !f)}
                aria-label="Flip card"
              >
                <div className="plastic-face plastic-front">
                  <div className="plastic-top">
                    <span className="plastic-network">{card.network}</span>
                    <span className="plastic-product">{card.product}</span>
                  </div>
                  <div className="plastic-chip" aria-hidden />
                  <div className="plastic-pan mono">•••• •••• •••• {card.last4}</div>
                  <div className="plastic-bottom">
                    <div>
                      <span className="plastic-label">Card holder</span>
                      <strong>{card.holder}</strong>
                    </div>
                    <div>
                      <span className="plastic-label">Valid</span>
                      <strong>{card.expiry}</strong>
                    </div>
                  </div>
                </div>
                <div className="plastic-face plastic-back">
                  <div className="plastic-stripe" />
                  <div className="plastic-cvv-box">
                    <span>CVV</span>
                    <strong>•••</strong>
                  </div>
                  <p className="plastic-back-note">Encrypted field. Unmask requires PIN + audited inquiry.</p>
                </div>
              </button>

              <div className="card-carousel-dots">
                {cards.map((c, i) => (
                  <button
                    key={c.id}
                    type="button"
                    className={`hero-dot ${i === active ? 'active' : ''}`}
                    aria-label={`Show ${c.id}`}
                    onClick={() => {
                      setActive(i);
                      setFlipped(false);
                      void loadDetail(c.id);
                    }}
                  />
                ))}
              </div>
              <p className="muted center-hint">Click card to flip · slideshow auto-advances</p>
            </section>

            <section className="glass-panel card-details-panel">
              <div className="panel-header">
                <div>
                  <h2 className="panel-title">{card.id}</h2>
                  <p className="muted panel-subtitle">{card.product}</p>
                </div>
                <span className={`chip ${statusChip}`}>{card.status}</span>
              </div>

              <dl className="detail-list">
                <div><dt>Card number</dt><dd className="mono">•••• •••• •••• {card.last4}</dd></div>
                <div><dt>Account number</dt><dd className="mono">{card.accountNo}</dd></div>
                <div><dt>Holder</dt><dd>{card.holder}</dd></div>
                <div><dt>Network</dt><dd>{card.network}</dd></div>
                <div><dt>Expiry</dt><dd>{card.expiry}</dd></div>
                <div><dt>Relationship</dt><dd className="mono">{card.relationshipNum || '—'}</dd></div>
              </dl>

              {siblings.length > 0 && (
                <div className="alert alert-info card-sibling-alert">
                  <strong>Same account, different card</strong>
                  <p>
                    {siblings.map((s) => `${s.id} (•••• ${s.last4})`).join(', ')} share account{' '}
                    <span className="mono">{card.accountNo}</span>.
                  </p>
                </div>
              )}

              <div className="card-detail-actions" style={{ flexDirection: 'column', alignItems: 'stretch' }}>
                <input
                  className="input"
                  type="password"
                  placeholder="PIN for unmask (optional)"
                  value={pin}
                  onChange={(e) => setPin(e.target.value)}
                  autoComplete="off"
                />
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => void runInquiry(false)}>
                    Masked inquiry
                  </button>
                  <button type="button" className="btn btn-primary btn-sm" onClick={() => void runInquiry(true)}>
                    Request unmask
                  </button>
                </div>
                {inquiryNote && <p className="muted" style={{ margin: 0 }}>{inquiryNote}</p>}
              </div>
            </section>
          </div>

          <section className="glass-panel animate-in animate-in-delay-2" style={{ marginTop: '1.25rem' }}>
            <h2 className="panel-title">Inventory</h2>
            <p className="muted panel-subtitle">{cards.length} card(s) in scope</p>
            <div className="card-inventory">
              {cards.map((c, i) => (
                <button
                  key={c.id}
                  type="button"
                  className={`inventory-item ${i === active ? 'active' : ''}`}
                  onClick={() => {
                    setActive(i);
                    setFlipped(false);
                    void loadDetail(c.id);
                  }}
                >
                  <span className={`inventory-swatch ${c.gradient}`} />
                  <div>
                    <strong>{c.holder !== '—' ? c.holder : c.id}</strong>
                    <p className="muted">•••• {c.last4} · {c.product}</p>
                  </div>
                  <span className={`chip ${String(c.status).toUpperCase().includes('ACTIVE') ? 'chip-success' : 'chip-warn'}`}>
                    {c.status}
                  </span>
                </button>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  );
}

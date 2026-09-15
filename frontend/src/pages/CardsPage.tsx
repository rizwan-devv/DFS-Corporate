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
  {
    id: 'CARD-1002',
    holder: 'Karachi Retail Hub',
    last4: '7734',
    accountNo: '1002••••9012',
    network: 'DFS Pay',
    status: 'Active',
    product: 'Franchise Debit',
    gradient: 'card-grad-2',
    expiry: '01/27',
    relationshipNum: '10029012',
  },
  {
    id: 'CARD-1003',
    holder: 'Lahore Express',
    last4: '2190',
    accountNo: '1008••••4410',
    network: 'DFS Pay',
    status: 'Pending',
    product: 'Corporate Prepaid',
    gradient: 'card-grad-3',
    expiry: '11/29',
    relationshipNum: '10084410',
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

function extractItems(payload: unknown): Record<string, unknown>[] {
  const root = asRecord(payload);
  const cms = asRecord(root.cms ?? root);
  const data = cms.data ?? cms.responseBody ?? cms.content ?? cms;
  if (Array.isArray(data)) return data.map(asRecord);
  const d = asRecord(data);
  for (const key of ['items', 'content', 'cards', 'records', 'list']) {
    const arr = d[key];
    if (Array.isArray(arr)) return arr.map(asRecord);
  }
  if (d.cardId || d.id) return [d];
  return [];
}

function mapCard(raw: Record<string, unknown>, index: number): UiCard {
  const pan = pick(raw, ['maskedPan', 'pan', 'cardNumber', 'cardNo']);
  const last4 =
    pick(raw, ['last4', 'lastFour']) ||
    (pan.replace(/\D/g, '').slice(-4) || '••••');
  const account = pick(raw, ['accountNumberMasked', 'accountNumber', 'accountNo', 'relationshipNum']);
  const status = pick(raw, ['cardStatusCode', 'status', 'cardStatus']) || 'UNKNOWN';
  const expM = pick(raw, ['expiryMonth', 'expMonth']);
  const expY = pick(raw, ['expiryYear', 'expYear']);
  const expiry =
    pick(raw, ['expiry', 'expiryDate']) ||
    (expM && expY ? `${expM}/${String(expY).slice(-2)}` : '—');

  return {
    id: pick(raw, ['cardId', 'id']) || `CMS-${index + 1}`,
    holder: pick(raw, ['holderName', 'cardHolder', 'customerName', 'name']) || 'Card holder',
    last4,
    accountNo: account || '••••',
    network: pick(raw, ['network', 'scheme', 'brand']) || 'DFS Pay',
    status,
    product: pick(raw, ['productName', 'productCode', 'product', 'cardType']) || 'Card',
    gradient: `card-grad-${(index % 3) + 1}`,
    expiry,
    relationshipNum: pick(raw, ['relationshipNum', 'relationshipNumber', 'accountNumber']),
    raw,
  };
}

export function CardsPage() {
  const { session } = useAuth();
  const [cards, setCards] = useState<UiCard[]>(DEMO);
  const [active, setActive] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(false);
  const [source, setSource] = useState<'demo' | 'cms'>('demo');
  const [error, setError] = useState<string | null>(null);
  const [cmsEnabled, setCmsEnabled] = useState(false);
  const [siblings, setSiblings] = useState<UiCard[]>([]);
  const [inquiryNote, setInquiryNote] = useState<string | null>(null);
  const [pin, setPin] = useState('');

  const card = cards[active] ?? cards[0];

  const load = useCallback(async () => {
    if (!session?.token) {
      setCards(DEMO);
      setSource('demo');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const status = await api<{ enabled?: boolean }>('/api/cms/cards/status', { token: session.token });
      setCmsEnabled(!!status.enabled);
      if (!status.enabled) {
        setCards(DEMO);
        setSource('demo');
        return;
      }
      const res = await api<unknown>('/api/cms/cards/search', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ page: 0, size: 50, sort: 'createdOn', sortDir: 'desc' }),
      });
      const items = extractItems(res).map(mapCard);
      if (items.length === 0) {
        setCards(DEMO);
        setSource('demo');
        setError('CMS returned no cards — showing design preview.');
      } else {
        setCards(items);
        setSource('cms');
        setActive(0);
      }
    } catch (e) {
      setCards(DEMO);
      setSource('demo');
      setError(e instanceof Error ? e.message : 'CMS unavailable — demo cards shown');
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
    if (s.includes('BLOCK') || s.includes('INACTIVE')) return 'chip-danger';
    return 'chip-warn';
  }, [card?.status]);

  async function loadDetail(cardId: string) {
    if (!session?.token || source !== 'cms') return;
    try {
      const res = await api<{ sameAccountDifferentCard?: boolean; sameAccountCards?: unknown; cms?: unknown }>(
        `/api/cms/cards/${encodeURIComponent(cardId)}`,
        { token: session.token },
      );
      const detailItems = extractItems(res);
      if (detailItems[0]) {
        setCards((prev) =>
          prev.map((c) => (c.id === cardId ? { ...mapCard(detailItems[0], 0), gradient: c.gradient } : c)),
        );
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
      setInquiryNote('Login + relationship number required for CMS inquiry.');
      return;
    }
    try {
      setInquiryNote(unmask ? 'Requesting unmask…' : 'Loading masked inquiry…');
      const res = await api<unknown>('/api/cms/cards/inquiry', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          relationshipNum: card.relationshipNum,
          ...(unmask && pin ? { pin } : {}),
        }),
      });
      setInquiryNote(unmask ? 'Unmask response received (see network/API).' : 'Masked inquiry OK.');
      console.info('CMS inquiry', res);
    } catch (e) {
      setInquiryNote(e instanceof Error ? e.message : 'Inquiry failed');
    }
  }

  if (!card) return null;

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Cards · CMS"
        title="Card details"
        subtitle="Inventory from CMS when enabled. Masked by default — unmask via secured inquiry."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        }
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Mask', title: 'Sensitive by default', body: 'PAN and CVV stay hidden until audited unmask.' },
          { accent: 'Siblings', title: 'Same account check', body: 'Detect multiple cards sharing one account number.' },
          { accent: 'Live', title: source === 'cms' ? 'CMS connected' : 'Design preview', body: cmsEnabled ? 'Backend CMS flag is on.' : 'Set DFS_CMS_API_ENABLED to go live.' },
        ]}
      />

      {error && <p className="api-banner">{error}</p>}
      <p className="muted" style={{ margin: 0 }}>
        Source: <strong>{source === 'cms' ? 'CMS API' : 'Demo preview'}</strong>
        {cmsEnabled ? ' · integration enabled' : ' · integration disabled'}
      </p>

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

          {(siblings.length > 0) && (
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
        <p className="muted panel-subtitle">{cards.length} card(s)</p>
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
                <strong>{c.id}</strong>
                <p className="muted">•••• {c.last4} · {c.holder}</p>
              </div>
              <span className={`chip ${String(c.status).toUpperCase().includes('ACTIVE') ? 'chip-success' : 'chip-warn'}`}>
                {c.status}
              </span>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}
